package com.aura.event;

import com.aura.dto.AuditLogEntryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogCreatedEvent {
    private UUID projectId;
    private AuditLogEntryDto entry;
}
