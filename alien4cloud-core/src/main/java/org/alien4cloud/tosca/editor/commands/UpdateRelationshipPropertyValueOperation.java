package org.alien4cloud.tosca.editor.commands;

import lombok.Getter;
import lombok.Setter;

/**
 * Update the value of a relationship property.
 */
@Getter
@Setter
public class UpdateRelationshipPropertyValueOperation extends AbstractRelationshipOperation {
    private String propertyName;
    private Object propertyValue;
}
