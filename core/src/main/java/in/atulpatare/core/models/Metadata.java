package in.atulpatare.core.models;

public class Metadata {
    public int sourceId;
    public String url;
    public String name;
    public String lang;
    public String logo;
    public String dev;
    public Boolean isActive;
    public Boolean isEnabled;

    public Metadata(int sourceId, String url, String name, String lang, String logo, String dev, Boolean isActive, Boolean isEnabled) {
        this.sourceId = sourceId;
        this.url = url;
        this.name = name;
        this.lang = lang;
        this.logo = logo;
        this.dev = dev;
        this.isActive = isActive;
        this.isEnabled = isEnabled;
    }

    public Metadata() {
        isActive = true;
        isEnabled = true;
    }

    @Override
    public String toString() {
        return "DataSource{" +
                "sourceId=" + sourceId +
                ", url='" + url + '\'' +
                '}';
    }
}
