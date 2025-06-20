package com.project.collab_docs.request;

import com.project.collab_docs.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVisibilityRequest {
    private Visibility visibility;
}
