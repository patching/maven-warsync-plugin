package com.github.patching.maven.warsync;

/**
 * @author jinggang.huangjg 2013-6-22 ÏÂÎç12:33:57
 */
public class FileSyncMap {

    private int    index;

    private String sourceFolder;
    private String destFolder;
    private String includePatterns;
    private String excludePatterns;
    private String filterFile;

    public FileSyncMap(int index){
        this.index = index;
    }

    public String getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(String sourceFolder) {
        this.sourceFolder = formatPath(sourceFolder);
    }

    public String getDestFolder() {
        return destFolder;
    }

    public void setDestFolder(String destFolder) {
        this.destFolder = formatPath(destFolder);
    }

    public String getIncludePatterns() {
        return includePatterns;
    }

    public void setIncludePatterns(String includePatterns) {
        this.includePatterns = includePatterns;
    }

    public String getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(String excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public String getFilterFile() {
        return filterFile;
    }

    public void setFilterFile(String filterFile) {
        this.filterFile = filterFile;
    }

    public String render() {
        String mapStr = "map|" + index + "=" + sourceFolder + "|";
        mapStr += destFolder == null ? ",|" : destFolder + "|";
        mapStr += includePatterns == null ? ",|" : includePatterns + "|";
        mapStr += excludePatterns == null ? ",|" : excludePatterns + "|";
        mapStr += filterFile == null ? "," : filterFile;
        return mapStr;
    }

    public static String formatPath(String path) {
        return path == null ? null : path.replaceAll("\\\\", "/").replaceFirst(":/", "\\\\:/");
    }
}
