/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

/**
 * IP 归属地五段结果值对象：国家、省份、城市、运营商、ISO 国家码。
 *
 * <p>五个字段<strong>永不为 null</strong>；内核保留值 {@code 0} / {@code Reserved}
 * 以及未命中结果统一表达为空串，调用方无需识别哨兵值。</p>
 */
public final class IpRegion {

    private final String country;
    private final String province;
    private final String city;
    private final String isp;
    private final String isoCode;

    private IpRegion(String country, String province, String city, String isp, String isoCode) {
        this.country = country;
        this.province = province;
        this.city = city;
        this.isp = isp;
        this.isoCode = isoCode;
    }

    /**
     * 构造五段结果；任一入参为 null 时该字段归一为空串（供 internal 解析层组装使用）。
     *
     * @param country 国家
     * @param province 省份
     * @param city 城市
     * @param isp 运营商
     * @param isoCode ISO 国家码
     * @return 五段结果对象
     */
    public static IpRegion of(String country, String province, String city, String isp, String isoCode) {
        return new IpRegion(
                country == null ? "" : country,
                province == null ? "" : province,
                city == null ? "" : city,
                isp == null ? "" : isp,
                isoCode == null ? "" : isoCode);
    }

    /**
     * 国家。
     *
     * @return 国家名，未命中时为空串
     */
    public String getCountry() {
        return country;
    }

    /**
     * 省份。
     *
     * @return 省份名，未命中时为空串
     */
    public String getProvince() {
        return province;
    }

    /**
     * 城市。
     *
     * @return 城市名，未命中时为空串
     */
    public String getCity() {
        return city;
    }

    /**
     * 运营商。
     *
     * @return 运营商名，未命中时为空串
     */
    public String getIsp() {
        return isp;
    }

    /**
     * ISO 国家码。
     *
     * @return ISO 国家码，未命中时为空串
     */
    public String getIsoCode() {
        return isoCode;
    }
}
