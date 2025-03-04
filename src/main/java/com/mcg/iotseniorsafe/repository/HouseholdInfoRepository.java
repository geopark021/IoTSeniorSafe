package com.mcg.iotseniorsafe.repository;

import com.mcg.iotseniorsafe.entity.HouseholdInfo;
import org.springframework.data.repository.CrudRepository;

import java.util.ArrayList;

public interface HouseholdInfoRepository extends CrudRepository<HouseholdInfo, Long> {

    @Override
    ArrayList<HouseholdInfo> findAll();
}
