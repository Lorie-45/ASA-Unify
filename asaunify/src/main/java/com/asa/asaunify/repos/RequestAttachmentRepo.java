package com.asa.asaunify.repos;


import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.RequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestAttachmentRepo extends JpaRepository<RequestAttachment, UUID> {

    List<RequestAttachment> findByRequest(Request request);

    void deleteByRequest(Request request);

    boolean existsByRequestAndFileName(Request request, String fileName);
}