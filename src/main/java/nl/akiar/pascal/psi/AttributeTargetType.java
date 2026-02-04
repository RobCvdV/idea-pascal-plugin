package nl.akiar.pascal.psi;

/**
 * The type of element that an attribute is attached to.
 */
public enum AttributeTargetType {
    /** Attribute on a type (class, record, interface) */
    TYPE,
    /** Attribute on a routine (procedure, function, method) */
    ROUTINE,
    /** Attribute on a property */
    PROPERTY,
    /** Attribute on a field */
    FIELD,
    /** Target type could not be determined */
    UNKNOWN
}
