package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.entity.HouseholdInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.ArrayList;

public interface HouseholdInfoRepository extends JpaRepository<HouseholdInfo, Long> {


    @Override
    ArrayList<HouseholdInfo> findAll();

    @Override
    Page<HouseholdInfo> findAll(Pageable pageable);

}
