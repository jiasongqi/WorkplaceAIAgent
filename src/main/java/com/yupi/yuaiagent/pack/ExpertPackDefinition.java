package com.yupi.yuaiagent.pack;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertPackDefinition {
    private String packId;
    private String displayName;
    private String description;
    @Builder.Default
    private List<String> agentCodes = new ArrayList<>();
    @Builder.Default
    private List<String> skillNames = new ArrayList<>();
    @Builder.Default
    private List<String> permissionProfiles = new ArrayList<>();
    @Builder.Default
    private boolean enabledByDefault = true;
}
