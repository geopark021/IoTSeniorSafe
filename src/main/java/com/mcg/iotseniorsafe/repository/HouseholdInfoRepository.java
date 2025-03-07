package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.entity.HouseholdInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HouseholdInfoRepository extends JpaRepository<HouseholdInfo, Long> {

    // 모든 가구에 부착된 기기 개수
    @Query(value = " SELECT count(led_mtchn_sn) FROM t_led_sensor_log ", nativeQuery = true)
    Long getDevicesCount();


    // 한 가구의 방위치별 여러 LED 정보 확인
    // 동일 기기번호로 여러 방에 설치
    @Query(value = "SELECT * "
            + "FROM t_led_sensor_log "
            + "WHERE led_mtchn_sn = :ledMtchnSn "
            + "AND (led_mtchn_sn, led_sensor_gbn, reg_dt) IN ( "
            + "SELECT led_mtchn_sn, led_sensor_gbn, MAX(reg_dt) "
            + "FROM t_led_sensor_log "
            + "WHERE led_mtchn_sn = :ledMtchnSn "
            + "GROUP BY led_mtchn_sn, led_sensor_gbn"
    + ")", nativeQuery = true)
    List<HouseholdInfo> findByLedMtchnSn(@Param("ledMtchnSn") String ledMtchnSn);
    // 지연시간 계산에 여러 값이 출력되는 문제 발생

    @Override
    Page<HouseholdInfo> findAll(Pageable pageable);

}
