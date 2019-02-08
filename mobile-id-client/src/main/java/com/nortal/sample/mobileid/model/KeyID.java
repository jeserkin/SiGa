//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.0 
// See <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.12.13 at 05:34:52 PM EET 
//


package com.nortal.sample.mobileid.model;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for KeyID.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="KeyID"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="RSA"/&gt;
 *     &lt;enumeration value="ECC"/&gt;
 *     &lt;enumeration value="SIGN_RSA"/&gt;
 *     &lt;enumeration value="SIGN_ECC"/&gt;
 *     &lt;enumeration value="AUTH_RSA"/&gt;
 *     &lt;enumeration value="AUTH_ECC"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "KeyID")
@XmlEnum
public enum KeyID {

    RSA,
    ECC,
    SIGN_RSA,
    SIGN_ECC,
    AUTH_RSA,
    AUTH_ECC;

    public String value() {
        return name();
    }

    public static KeyID fromValue(String v) {
        return valueOf(v);
    }

}
