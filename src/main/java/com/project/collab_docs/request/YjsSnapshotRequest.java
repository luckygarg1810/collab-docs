package com.project.collab_docs.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YjsSnapshotRequest {
    private String yjsRoomId;
    private byte[] snapshot;
}
