package cn.cdtu.lw.servlet;

import cn.cdtu.lw.annotation.Controller;
import cn.cdtu.lw.annotation.RequestMapping;
import cn.cdtu.lw.annotation.RequestParam;
import cn.cdtu.lw.context.ApplicationContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author
 */
public class DispatcherServlet extends HttpServlet {

    private static final String LOCATION = "ContextConfigLocation";
    private List<Handler> handlerMapping = new ArrayList();
    private Map<Handler, HandlerAdapter> adapterMap = new HashMap<>();

    @Override
    public void init(ServletConfig servletConfig) {

        //IOC容器必须先启动
        ApplicationContext context = new ApplicationContext(servletConfig.getInitParameter(LOCATION));

        //请求解析
        initMultipartResolver(context);
        //多语言国际化
        initLocalResolver(context);
        //主题view层
        initThemeResolver(context);
        /*********核心********/
        //解析URL和method得关联关系
        initHandlerMapping(context);
        //适配器
        initHandlerAdapter(context);
        /********核心********/
        //异常解析
        initHandlerException(context);
        //视图转发
        initRequestToViewNameTranslator(context);
        //解析模板中得内容(拿到服务器传过来得数据,生成html)
        initViewResolvers(context);
        //
        initFlashMapping(context);
    }


    private void initFlashMapping(ApplicationContext context) {
    }

    private void initViewResolvers(ApplicationContext context) {
    }

    private void initRequestToViewNameTranslator(ApplicationContext context) {
    }

    private void initHandlerException(ApplicationContext context) {
    }

    /**
     * 适配器 匹配过程
     * 动态匹配参数
     * 动态赋值
     * @param context
     */
    private void initHandlerAdapter(ApplicationContext context) {
        if (handlerMapping.isEmpty()) {
            return;
        }
        //参数类型作为key，参数的索引号作为值
        Map<String,Integer> paramMapping = new HashMap<String,Integer>();

        //只需要取出来具体的某个方法
        for (Handler handler : handlerMapping) {

            //把这个方法上面所有的参数全部获取到
            Class<?> [] paramsTypes = handler.method.getParameterTypes();

            //有顺序，但是通过反射，没法拿到我们参数名字
            //匹配自定参数列表
            for (int i = 0;i < paramsTypes.length ; i ++) {

                Class<?> type = paramsTypes[i];

                if(type == HttpServletRequest.class ||
                        type == HttpServletResponse.class){
                    paramMapping.put(type.getName(), i);
                }
            }


            //这里是匹配Request和Response
            Annotation[][] pa = handler.method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i ++) {
                for(Annotation a : pa[i]){
                    if(a instanceof RequestParam){
                        String paramName = ((RequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramMapping.put(paramName, i);
                        }

                    }
                }
            }

            adapterMap.put(handler, new HandlerAdapter(paramMapping));
        }
    }

    /**
     * 找到所有controller中所有被RequestMapping 标注得方法
     *
     * @param context
     */
    private void initHandlerMapping(ApplicationContext context) {
        Map<String, Object> IOC = context.getAll();
        if (IOC.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : IOC.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            StringBuffer url = new StringBuffer();
            if (clazz.isAnnotationPresent(Controller.class)) {
                if (clazz.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                    url.append(requestMapping.value());
                }
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                        String regex = url.append(requestMapping.value()).toString().replace("/+", "/");
                        Pattern pattern = Pattern.compile(regex);
                        this.handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                    }

                }
            }
        }
    }


    private void initThemeResolver(ApplicationContext context) {
    }

    private void initLocalResolver(ApplicationContext context) {
    }

    private void initMultipartResolver(ApplicationContext context) {

    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("进入get");
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception,MSG:" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        //从handlerMapping取handler
        Handler handler = getHandler(req);
        if (handler == null) {
            resp.getWriter().write("404,Not Found");
            return;
        }
        //再取出适配器
        HandlerAdapter adapter = getHandlerAdapter(handler);
        //再通过适配器执行对应得方法
        adapter.handler(req, resp, handler);
    }

    private HandlerAdapter getHandlerAdapter(Handler handler) {
        if (adapterMap.isEmpty()) {
            return null;
        }
        return adapterMap.get(handler);
    }

    private Handler getHandler(HttpServletRequest servletRequest) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = servletRequest.getRequestURI();

        String contextPath = servletRequest.getContextPath();

        url = url.replace(contextPath, "").replace("/+", "/");

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if (matcher.matches()) {
                return handler;
            }

        }
        return null;
    }

    /**
     *
     */
    private class Handler {
        protected Pattern pattern;
        protected Method method;
        protected Object controller;

        protected Handler(Pattern pattern,Object controller, Method method) {
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;
        }
    }

    /**
     * 方法适配器
     */
    private class HandlerAdapter {
        private Map<String, Integer> adapters = new HashMap<>();

        public HandlerAdapter(Map<String, Integer> adapters) {
            this.adapters = adapters;
        }

        public void handler(HttpServletRequest req, HttpServletResponse resp, Handler handler) throws InvocationTargetException, IllegalAccessException, IOException {
            Class<?> [] paramTypes = handler.method.getParameterTypes();
            Object [] paramValues = new Object[paramTypes.length];

            Map<String,String[]> params = req.getParameterMap();

            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                if(!this.adapters.containsKey(param.getKey())){continue;}

                int index = this.adapters.get(param.getKey());

                //单个赋值是不行的
                paramValues[index] = castStringValue(value,paramTypes[index]);
            }

            //request 和 response 要赋值
            String reqName = HttpServletRequest.class.getName();
            if(this.adapters.containsKey(reqName)){
                int reqIndex = this.adapters.get(reqName);
                paramValues[reqIndex] = req;
            }


            String resqName = HttpServletResponse.class.getName();
            if(this.adapters.containsKey(resqName)){
                int respIndex = this.adapters.get(resqName);
                paramValues[respIndex] = resp;
            }

            Object r = handler.method.invoke(handler.controller, paramValues);
        }

        private Object castStringValue(String value, Class<?> paramType) {

            if(paramType == String.class){
                return value;
            }else if(paramType == Integer.class){
                return Integer.valueOf(value);
            }else if(paramType == int.class){
                return Integer.valueOf(value).intValue();
            }else{
                return null;
            }
        }
        }

    
}
