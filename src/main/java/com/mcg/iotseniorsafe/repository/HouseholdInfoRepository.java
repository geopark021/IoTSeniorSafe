package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.entity.HouseholdInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdInfoRepository extends JpaRepository<HouseholdInfo, Long> {


    @Override
    Page<HouseholdInfo> findAll(Pageable pageable);

}
