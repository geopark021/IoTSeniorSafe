package com.mcg.iotseniorsafe.controller;

import com.mcg.iotseniorsafe.dto.DevicesStats;
import com.mcg.iotseniorsafe.entity.HouseholdInfo;
import com.mcg.iotseniorsafe.repository.HouseholdInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class HouseholdInfoController {

    @Autowired
    private HouseholdInfoRepository householdInfoRepository;

    // 데이터 현황
    // IoT 장치 현황 - 현재 기기 개수(가구 수) 데이터만 있음
    @GetMapping("/status")
    public String showDashboard(Model model)
    {
        // 1. DB에서 기기 개수 불러오기
        Long iotEnabledHouseholds = householdInfoRepository.getDevicesCount();

        // DevicesStats DTO 생성 (나머지 값은 임의로 설정)
        DevicesStats deviceStats = new DevicesStats(
                300,                      // totalHouseholds (임의 값)
                iotEnabledHouseholds,     // iotEnabledHouseholds (DB에서 가져온 값)
                285,                      // powerConnectedHouseholds (임의 값)
                12                        // anomaliesPerHousehold (임의 값)
        );
        // 2. 모델에 데이터 등록
        model.addAttribute("deviceStats", deviceStats);


        // 3. 뷰에 설정
        return "household/iotdashboard";
    }

    // DB에 저장된 데이터 조회
    // 한 가구의 단일 데이터 조회 (한 가구의 여러 데이터 조회 변경)
    @GetMapping("/status/{ledMtchnSn}")
    public String show(@PathVariable String ledMtchnSn, Model model)
    {
        log.info("ledMtchnSn = " + ledMtchnSn);
        // 1. id를 조회해 DB에서 해당 데이터 가져오기
        List<HouseholdInfo> householdInfoList = householdInfoRepository.findByLedMtchnSn(ledMtchnSn);

        // 조회시 시각
        LocalDateTime now = LocalDateTime.now();

        // LED ON 유지시간 계산 - 현재 시간만 넘겨주면 뷰로 전달하면 됨
        List<Map<String, Object>> ledOnTimes = new ArrayList<>();
        for (HouseholdInfo info : householdInfoList) {
            long hours = 0;
            long minutes = 0;
            if (info.getRegDt() != null) {
                Duration duration = Duration.between(info.getRegDt(), now);
                hours = duration.toHours();
                minutes = duration.toMinutes() % 60;
            }
            ledOnTimes.add(Map.of("ledMtchnSn", info.getLedMtchnSn(), "hours", hours, "minutes", minutes));
        }

        // 2. 모델에 데이터 등록
        model.addAttribute("householdInfoList", householdInfoList);
        model.addAttribute("ledOnTimes", ledOnTimes);

        // 3. 뷰 페이지 반환
        return "household/show";
    }


    // 데이터 목록 조회
    // 페이지 번호 주면 데이터 20개 제공
    public Page<HouseholdInfo> getList(int page)
    {
        Pageable pageable = PageRequest.of(page, 10); // 10개 행
        return this.householdInfoRepository.findAll(pageable);
    }

    @GetMapping("/status/list")
    public String index(Model model,
                        @RequestParam(defaultValue = "0") int page, // 현재 페이지
                        @RequestParam(defaultValue = "20") int size) {
        // 1. 페이지 번호(page)와 페이지 크기(size)를 동적으로 지정
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending()); // id기준 최신순으로

        // 2. 페이징된 데이터 조회
        Page<HouseholdInfo> householdInfoPage = householdInfoRepository.findAll(pageable); // ArrayList 타입으로 받음
        // 페이지 그룹
        int pageGroupStart = (page / 10) * 10;
        int pageGroupEnd = Math.min(pageGroupStart + 9, householdInfoPage.getTotalPages() - 1);

        // 가구당 데이터 조회 시각 기준으로 LED가 켜진지 얼마나 지났는지 표시


        // 3. 모델에 데이터 등록 (페이징 정보 포함)
        model.addAttribute("householdinfoPage", householdInfoPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", householdInfoPage.getTotalPages());
        model.addAttribute("pageGroupStart", pageGroupStart);
        model.addAttribute("pageGroupEnd", pageGroupEnd);

        // 4. 뷰 페이지 설정
        return "household/list";
    }







}
