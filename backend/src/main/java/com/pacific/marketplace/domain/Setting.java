package com.pacific.marketplace.domain;

import jakarta.persistence.*;

/** Small key/value store for admin-editable marketplace settings (e.g. the default commission). */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 60)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 200)
    private String value;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
