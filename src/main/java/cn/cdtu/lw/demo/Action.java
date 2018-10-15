package cn.cdtu.lw.demo;

import cn.cdtu.lw.annotation.*;
import cn.cdtu.lw.demo.service.INamedService;
import cn.cdtu.lw.demo.service.IService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author  on 2018/10/14.
 * @version 1.0
 */
@Controller
@RequestMapping("/web")
public class Action {

    @Autowired
    private IService service;

    @Autowired("named")
    private INamedService namedService;

    @RequestMapping("/get")
    @ResponseBody
    public void get(HttpServletRequest request, HttpServletResponse response, @RequestParam("name") String name) throws IOException {
        response.setCharacterEncoding("utf-8");
        response.getWriter().write("收到得名字是:" + name);
    }
}
