package com.catsflow.catsflow.hello;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;

@Controller
public class HelloController {

    private final HelloService service;

    public HelloController(HelloService helloService) {
        this.service = helloService;
    }

    @GetMapping("/hello")
    public String getHello() {
        // hello.htmlに画面遷移
        return "hello";
    }

    @PostMapping("/hello")
    public String postRequest(@RequestParam("text1") String str, Model model) {
        model.addAttribute("sample", str);
        return "hello/response";
    }

    @PostMapping("/hello/db")
    public String postDbRequest(@RequestParam("text2") String id, Model model) {
        // 1件検索
        List<Map<String, Object>> transactions = service.getTransactions(Long.valueOf(id));
        // 検索結果をmodelに登録
        model.addAttribute("transactions", transactions);
        // hello/db.htmlに画面遷移
        return "hello/db";
    }
}