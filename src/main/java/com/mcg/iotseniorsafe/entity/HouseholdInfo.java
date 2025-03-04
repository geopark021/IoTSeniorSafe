package com.mcg.iotseniorsafe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Table(name = "t_led_sensor_log")
public class HouseholdInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LED_SENSOR_LOG_SERNO", nullable = false)
    private Long id;

    @Column(name = "LED_MTCHN_SN", nullable = false)
    private String ledMtchnSn;

    @Column(name = "LED_SENSOR_GBN", nullable = false)
    private String ledSensorGbn;

    @Column(name = "REG_DT",columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime regDt;



}
