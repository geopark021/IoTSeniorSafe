package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.entity.HouseholdInfo;
import com.mcg.iotseniorsafe.repository.HouseholdInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
public class HouseholdInfoController {

    @Autowired
    private HouseholdInfoRepository householdInfoRepository;
    @GetMapping("/status")
    public String showHouseholdStatus()
    {
        return "layouts/sidebar";
    }

    // DB에 저장된 데이터 조회
    // 단일 데이터 조회
    @GetMapping("/status/{id}")
    public String show(@PathVariable Long id, Model model)
    {
        log.info("id = " + id);
        // 1. id를 조회해 DB에서 해당 데이터 가져오기
        HouseholdInfo householdInfoEntity = householdInfoRepository.findById(id).orElse(null);
        // 2. 모델에 데이터 등록
        model.addAttribute("householdinfo", householdInfoEntity);
        // 3. 뷰 페이지 반환
        return "household/show";
    }


    // 데이터 목록 조회
    @GetMapping("/status/list")
    public String index(Model model)
    {
        // 1. 모든 데이터 DB에서 가져오기
        ArrayList<HouseholdInfo> householdInfoEntityList = householdInfoRepository.findAll();
        // 2. 모델에 데이터 등록
        model.addAttribute("householdinfoList", householdInfoEntityList);
        // 3. 뷰 페이지 설정
        return "household/list"; // 페이징 미적용
    }

    // 시간별 기기 사용량 조회


}
