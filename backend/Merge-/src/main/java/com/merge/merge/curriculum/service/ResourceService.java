package com.merge.merge.curriculum.service;

import com.merge.merge.curriculum.models.Resource;

import java.util.List;
import java.util.UUID;

public interface ResourceService {
    Resource create(UUID conceptId, String type, String title, String url);
    List<Resource> listByConceptId(UUID conceptId);
    void delete(UUID resourceId);
    long countByConceptId(UUID conceptId);
}
