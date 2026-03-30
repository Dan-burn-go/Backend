package com.danburn.map.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 복합키를 위한 IdClass

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AlternativeLocationId implements Serializable {
    private Long location;
    private Long alternativeLocation;
}
