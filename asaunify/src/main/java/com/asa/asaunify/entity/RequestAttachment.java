package com.asa.asaunify.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    // Original file name as uploaded by the user
    @Column(name = "file_name", nullable = false)
    private String fileName;

    // Path on the server filesystem where the file is stored
    // e.g. /uploads/requests/CN-001/report.pdf
    @Column(name = "file_path", nullable = false)
    private String filePath;

    // File size in bytes — used for display and validation
    @Column(name = "file_size")
    private Long fileSize;

    // MIME type e.g. application/pdf, image/png, application/vnd.ms-excel
    @Column(name = "content_type")
    private String contentType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}