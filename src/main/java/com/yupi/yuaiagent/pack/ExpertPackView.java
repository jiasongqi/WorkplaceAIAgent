package com.yupi.yuaiagent.pack;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertPackView {
    private String packId;
    private String displayName;
    private String description;
    private List<String> agentCodes;
    private List<String> skillNames;
    private List<String> permissionProfiles;
    private boolean enabled;
}
