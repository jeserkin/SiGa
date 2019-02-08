//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.0 
// See <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.12.13 at 05:34:52 PM EET 
//


package com.nortal.sample.mobileid.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AbstractGetStatusRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AbstractGetStatusRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://www.sk.ee/DigiDocService/DigiDocService_2_3.wsdl}SessionAwareType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="WaitSignature" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AbstractGetStatusRequestType", propOrder = {
    "waitSignature"
})
@XmlSeeAlso({
    GetMobileSignHashStatusRequest.class,
    GetMobileAuthenticateStatusRequest.class
})
public abstract class AbstractGetStatusRequestType
    extends SessionAwareType
{

    @XmlElement(name = "WaitSignature")
    protected Boolean waitSignature;

    /**
     * Gets the value of the waitSignature property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isWaitSignature() {
        return waitSignature;
    }

    /**
     * Sets the value of the waitSignature property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setWaitSignature(Boolean value) {
        this.waitSignature = value;
    }

}
