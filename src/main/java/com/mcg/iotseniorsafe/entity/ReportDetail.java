package com.mcg.iotseniorsafe.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "report_detail")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ReportDetail {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long detailId;

    private Long reportId;

    @Lob                // TEXT 컬럼 매핑
    private String description;
}
