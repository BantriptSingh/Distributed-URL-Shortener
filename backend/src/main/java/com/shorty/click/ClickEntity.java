package com.shorty.click;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "clicks")
public class ClickEntity {

    @Id
    private Long id;

    @Column(name = "url_id", nullable = false)
    private Long urlId;

    @Column(name = "stream_id", unique = true)
    private String streamId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(name = "country", length = 8)
    private String country;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "browser", length = 64)
    private String browser;

    @Column(name = "os", length = 64)
    private String os;

    @Column(name = "referrer")
    private String referrer;

    @Column(name = "is_bot", nullable = false)
    private boolean bot;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUrlId() {
        return urlId;
    }

    public void setUrlId(Long urlId) {
        this.urlId = urlId;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }
}
