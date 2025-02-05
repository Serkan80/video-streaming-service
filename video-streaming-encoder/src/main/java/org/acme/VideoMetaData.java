package org.acme;

public record VideoMetaData(String videoId, String inputFilePath, String outputFolder, String bitrate, String encoding) {

    public String getResolutionSize() {
        return switch (this.bitrate) {
            case "480p" -> "854x480";
            case "720p" -> "1280x720";
            case "1020p" -> "1920x1080";
            case "2k" -> "2560x1440";
            case "4k" -> "3840x2160";
            default -> throw new IllegalArgumentException("Invalid resolution: " + this.bitrate);
        };
    }

    public String getBitrate() {
        return switch (this.bitrate) {
            case "480p" -> "800k";
            case "720p" -> "2800k";
            case "1020p" -> "4000k";
            case "2k" -> "7000k";
            case "4k" -> "14000k";
            default -> throw new IllegalArgumentException("Invalid resolution: " + this.bitrate);
        };
    }

    public String getMaxRate() {
        return switch (this.bitrate) {
            case "480p" -> "1000k";
            case "720p" -> "3500k";
            case "1020p" -> "5000k";
            case "2k" -> "10000k";
            case "4k" -> "17500k";
            default -> throw new IllegalArgumentException("Invalid resolution: " + this.bitrate);
        };
    }

    public String getBufSize() {
        return switch (this.bitrate) {
            case "480p" -> "2000k";
            case "720p" -> "7000k";
            case "1020p" -> "10000k";
            case "2k" -> "20000k";
            case "4k" -> "35000k";
            default -> throw new IllegalArgumentException("Invalid resolution: " + this.bitrate);
        };
    }
}