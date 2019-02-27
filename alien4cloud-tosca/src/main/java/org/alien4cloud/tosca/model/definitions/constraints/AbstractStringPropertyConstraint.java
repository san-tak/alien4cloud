package org.alien4cloud.tosca.model.definitions.constraints;

import org.alien4cloud.tosca.normative.types.IPropertyType;
import alien4cloud.tosca.properties.constraints.ConstraintUtil;
import org.alien4cloud.tosca.exceptions.ConstraintValueDoNotMatchPropertyTypeException;
import org.alien4cloud.tosca.exceptions.ConstraintViolationException;

public abstract class AbstractStringPropertyConstraint extends AbstractPropertyConstraint {
    protected abstract void doValidate(String propertyValue) throws ConstraintViolationException;

    @Override
    public void validate(Object propertyValue) throws ConstraintViolationException {
        if (propertyValue == null) {
            throw new ConstraintViolationException("Value to validate is null");
        }
        if (!(propertyValue instanceof String)) {
            throw new ConstraintViolationException("This constraint can only be applied on String value");
        }
        doValidate((String) propertyValue);
    }

    @Override
    public void initialize(IPropertyType<?> propertyType) throws ConstraintValueDoNotMatchPropertyTypeException {
        ConstraintUtil.checkStringType(propertyType);
    }
}
