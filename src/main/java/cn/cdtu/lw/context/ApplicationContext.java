package cn.cdtu.lw.context;



import cn.cdtu.lw.annotation.Autowired;
import cn.cdtu.lw.annotation.Controller;
import cn.cdtu.lw.annotation.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author  on 2018/10/14.
 * @version 1.0
 */
public class ApplicationContext {

    private static final String SCAN_PACKAGE = "scanPackage";
    private Map<String, Object> instanceMapping = new ConcurrentHashMap<>();
    /**
     *内部使用得Beandefinition
     */
    private List<String> classCache = new ArrayList<>();

    public ApplicationContext(String location) {
        //先加载配置文件
        InputStream inputStream = null;
        try {
            //定位
            inputStream = this.getClass().getClassLoader().getResourceAsStream(location);
            Properties config = new Properties();
            //载入
            config.load(inputStream);
            //得到要扫描得包名
            String packageName = config.getProperty(SCAN_PACKAGE);
            //注册 把所有得class找出来
            doRegister(packageName);
            //初始化 只要循环class
            doCreateBean();
            //注入
            populate();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void populate() {
        if (instanceMapping.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : instanceMapping.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                Autowired autowired = field.getAnnotation(Autowired.class);
                String id = autowired.value().trim();
                //id未空 没有自定义名字，根据类型匹配
                if ("".equals(id)) {
                    id = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), instanceMapping.get(id));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("IOC容器已经初始化完成");
    }

    /**
     * 把所有符合条件得class注册到缓存中
     *
     * @param packageName
     */
    private void doRegister(String packageName) {
        URL url = this.getClass()
                .getClassLoader()
                .getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            //如果是文件夹，继续递归
            if (file.isDirectory()) {
                doRegister(packageName + "." + file.getName());
            }
            //不是文件夹，就是class文件，加载到缓存中
            else {
                classCache.add(packageName + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    private void doCreateBean() {
        if (classCache.size() == 0) {
            return;
        }
        try {

            for (String className : classCache) {
                Class<?> clazz = Class.forName(className);
                //只要加了注解得都要初始化，Service,Controller,compent
                if (clazz.isAnnotationPresent(Controller.class)) {
                    String id = lowerFirstChar(clazz.getSimpleName());
                    instanceMapping.put(id, clazz.getConstructor().newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    //如果service有名字，优先用他定义得名字
                    Service service = clazz.getAnnotation(Service.class);
                    String id = service.value().trim();
                    if (!"".equals(id)) {
                        instanceMapping.put(id, clazz.getConstructor().newInstance());
                        continue;
                    }
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        instanceMapping.put(i.getName(), clazz.getConstructor().newInstance());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String lowerFirstChar(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return new String(chars);
    }

    public Object getBean(String name) {
        return null;
    }

    public Map<String, Object> getAll() {
        return instanceMapping;
    }
}
