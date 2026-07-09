package com.merge.merge.curriculum.service.impl;

import com.merge.merge.curriculum.models.Resource;
import com.merge.merge.curriculum.repository.ResourceRepository;
import com.merge.merge.curriculum.service.ResourceService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;

    public ResourceServiceImpl(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Override
    public Resource create(UUID conceptId, String type, String title, String url) {
        Resource resource = new Resource(conceptId, type, title, url);
        return resourceRepository.save(resource);
    }

    @Override
    public List<Resource> listByConceptId(UUID conceptId) {
        return resourceRepository.findByConceptId(conceptId);
    }

    @Override
    public void delete(UUID resourceId) {
        resourceRepository.deleteById(resourceId);
    }

    @Override
    public long countByConceptId(UUID conceptId) {
        return resourceRepository.countByConceptId(conceptId);
    }
}
