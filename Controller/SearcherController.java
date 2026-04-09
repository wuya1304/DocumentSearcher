package com.springboot.documentsearcher.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.documentsearcher.Service.DocSearcher;
import com.springboot.documentsearcher.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearcherController {
    @Autowired
    private DocSearcher searcher;
    @Autowired
    private ObjectMapper objectMapper;

    @RequestMapping(value = "/search", produces = "application/json;charset=utf-8")
    @ResponseBody
    public String search(@RequestParam("query") String query) throws JsonProcessingException {
        List<Result> resultList = searcher.searcher(query);
        return objectMapper.writeValueAsString(resultList);
    }
}
