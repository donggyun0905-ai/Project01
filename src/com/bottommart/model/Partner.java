package com.bottommart.model;

public class Partner {

    private Long partnerId;
    private String name;
    private String type; // SUPPLIER / CUSTOMER
    private String contact;

    public Partner() {
    }

    public Partner(Long partnerId, String name, String type, String contact) {
        this.partnerId = partnerId;
        this.name = name;
        this.type = type;
        this.contact = contact;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    @Override
    public String toString() {
        return "Partner{partnerId=" + partnerId + ", name='" + name + "', type='" + type
                + "', contact='" + contact + "'}";
    }
}
