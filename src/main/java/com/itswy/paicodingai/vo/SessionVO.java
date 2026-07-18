package com.itswy.paicodingai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionVO {
    private String sessionId;
    private String title;
    private String describe;
    private List<String> examples;
}
