package com.example.pace.data.model.response

import com.tickaroo.tikxml.annotation.Xml
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.PropertyElement

// 실시간 지하철 데이터 클래스
data class SubwayTransitResponse<T>(
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String,
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("result") val result: JsonElement?
){
    fun returnToList(gson: Gson): List<SubwayTransitResult>{
        return if(result?.isJsonArray == true){
            result.asJsonArray.map{
                gson.fromJson(it, SubwayTransitResult::class.java)
            }
        }else{
            emptyList()
        }
    }
}

data class SubwayTransitResult(
    @SerializedName("subwayId") val subwayId: String,
    @SerializedName("trainLineNm") val trainLineNm: String,
    @SerializedName("barvlDt") val barvlDt: String,
    @SerializedName("arvlMsg2") val arvlMsg2: String,
    @SerializedName("arvlMsg3") val arvlMsg3: String,
    @SerializedName("bstatnNm") val bstatnNm: String,
    @SerializedName("updnLine") val updnLine: String,
    @SerializedName("beforeSubwayCount") val beforeSubwayCount: Int
)

// 지하철 시간표 데이터 클래스
data class TimetableResponse(
    @SerializedName("header") val header: TimetableHeader,
    @SerializedName("body") val body: TimetableBody
)

data class TimetableHeader(
    @SerializedName("resultCode") val resultCode: String,
    @SerializedName("resultMsg") val resultMsg: String,
)

data class TimetableBody(
    @SerializedName("items") val items: TimetableItem,
    @SerializedName("pageNo") val pageNo: String,
    @SerializedName("numOfRows") val nowOfRows: String,
    @SerializedName("totalCount") val totalCount: String
)

data class TimetableItem(
    @SerializedName("item") val item: List<SubwayTimetableItem>
)

data class SubwayTimetableItem(
    @SerializedName("trainno") val trainNo: String?,
    @SerializedName("trainKnd") val trainKnd: String?,
    @SerializedName("upbdnbSe") val upbdnbSe: String?,
    @SerializedName("wkndSe") val wkndSe: String?,
    @SerializedName("lineNm") val lineNm: String?,
    @SerializedName("brlnNm") val brlnNm: String?,
    @SerializedName("stnCd") val stnCd: String?,
    @SerializedName("stnNo") val stnNo: String?,
    @SerializedName("stnNm") val stnNm: String?,
    @SerializedName("dptreLineNm") val dptreLineNm: String?,
    @SerializedName("dptreStnCd") val dptreStnCd: String?,
    @SerializedName("dptreStnNm") val dptreStnNm: String?,
    @SerializedName("dptreStnNo") val dptreStnNo: String?,
    @SerializedName("arvlLineNm") val arvlLineNm: String?,
    @SerializedName("arvlStnCd") val arvlStnCd: String?,
    @SerializedName("arvlStnNm") val arvlStnNm: String?,
    @SerializedName("arvlStnNo") val arvlStnNo: String?,
    @SerializedName("trainDptreTm") val trainDptreTm: String?,
    @SerializedName("trainArvlTm") val trainArvlTm: String?,
    @SerializedName("etrnYn") val etrnYn: String?,
    @SerializedName("lnkgTrainno") val lnkgTrainno: String?,
    @SerializedName("tmprTmtblYn") val tmprTmtblYn: String?,
    @SerializedName("vldBgngDt") val vldBgngDt: String?,
    @SerializedName("vldEndDt") val vldEndDt: String?,
    @SerializedName("crtrYmd") val crtrYmd: String?
)

// 실시간 버스 데이터 클래스
@Xml(name = "ServiceResult")
data class BusTransitResponse(
    @PropertyElement(name = "comMsgHeader") val comMsgHeader: String,
    @Element(name = "msgHeader") val header: MsgHeader,
    @Element(name = "msgBody") val body: MsgBody
)

@Xml(name = "msgHeader")
data class MsgHeader(
    @PropertyElement(name = "headerCd") val headerCd: String,
    @PropertyElement(name = "headerMsg") val headerMsg: String,
    @PropertyElement(name = "itemCount") val itemCount: String,
)

@Xml(name = "msgBody")
data class MsgBody(
    @Element(name = "itemList") val itemList: BusItemList
)

@Xml(name = "itemList")
data class BusItemList(
    @PropertyElement(name = "arrmsg1") val arrmsg1: String?,
    @PropertyElement(name = "arrmsg2") val arrmsg2: String?,
    @PropertyElement(name = "arsId") val arsId: String?,
    @PropertyElement(name = "avgCf1") val avgCf1: String?,
    @PropertyElement(name = "avgCf2") val avgCf2: String?,
    @PropertyElement(name = "brdrde_Num1") val brdrde_Num1: String?,
    @PropertyElement(name = "brdrde_Num2") val brdrde_Num2: String?,
    @PropertyElement(name = "brerde_Div1") val brerde_Div1: String?,
    @PropertyElement(name = "brerde_Div2") val brerde_Div2: String?,
    @PropertyElement(name = "busRouteAbrv") val busRouteAbrv: String?,
    @PropertyElement(name = "busRouteId") val busRouteId: String?,
    @PropertyElement(name = "busType1") val busType1: String?,
    @PropertyElement(name = "busType2") val busType2: String?,
    @PropertyElement(name = "deTourAt") val deTourAt: String?,
    @PropertyElement(name = "dir") val dir: String?,
    @PropertyElement(name = "expCf1") val expCf1: String?,
    @PropertyElement(name = "expCf2") val expCf2: String?,
    @PropertyElement(name = "exps1") val exps1: String?,
    @PropertyElement(name = "exps2") val exps2: String?,
    @PropertyElement(name = "firstTm") val firstTm: String?,
    @PropertyElement(name = "full1") val full1: String?,
    @PropertyElement(name = "full2") val full2: String?,
    @PropertyElement(name = "goal1") val goal1: String?,
    @PropertyElement(name = "goal2") val goal2: String?,
    @PropertyElement(name = "isArrive1") val arrive1: String?,
    @PropertyElement(name = "isArrive2") val arrive2: String?,
    @PropertyElement(name = "isLast1") val last1: String?,
    @PropertyElement(name = "isLast2") val last2: String?,
    @PropertyElement(name = "kalCf1") val kalCf1: String?,
    @PropertyElement(name = "kalCf2") val kalCf2: String?,
    @PropertyElement(name = "kals1") val kals1: String?,
    @PropertyElement(name = "kals2") val kals2: String?,
    @PropertyElement(name = "lastTm") val lastTm: String?,
    @PropertyElement(name = "mkTm") val mkTm: String?,
    @PropertyElement(name = "namin2Sec1") val namin2Sec1: String?,
    @PropertyElement(name = "namin2Sec2") val namin2Sec2: String?,
    @PropertyElement(name = "neuCf1") val neuCf1: String?,
    @PropertyElement(name = "neuCf2") val neuCf2: String?,
    @PropertyElement(name = "neus1") val neus1: String?,
    @PropertyElement(name = "neus2") val neus2: String?,
    @PropertyElement(name = "nextBus") val nextBus: String?,
    @PropertyElement(name = "nmain2Ord1") val nmain2Ord1: String?,
    @PropertyElement(name = "nmain2Ord2") val nmain2Ord2: String?,
    @PropertyElement(name = "nmain2Stnid1") val nmain2Stnid1: String?,
    @PropertyElement(name = "nmain2Stnid2") val nmain2Stnid2: String?,
    @PropertyElement(name = "nmain3Ord1") val nmain3Ord1: String?,
    @PropertyElement(name = "nmain3Ord2") val nmain3Ord2: String?,
    @PropertyElement(name = "nmain3Sec1") val nmain3Sec1: String?,
    @PropertyElement(name = "nmain3Sec2") val nmain3Sec2: String?,
    @PropertyElement(name = "nmain3Stnid1") val nmain3Stnid1: String?,
    @PropertyElement(name = "nmain3Stnid2") val nmain3Stnid2: String?,
    @PropertyElement(name = "nmainOrd1") val nmainOrd1: String?,
    @PropertyElement(name = "nmainOrd2") val nmainOrd2: String?,
    @PropertyElement(name = "nmainSec1") val nmainSec1: String?,
    @PropertyElement(name = "nmainSec2") val nmainSec2: String?,
    @PropertyElement(name = "nmainStnid1") val nmainStnid1: String?,
    @PropertyElement(name = "nmainStnid2") val nmainStnid2: String?,
    @PropertyElement(name = "nstnId1") val nstnId1: String?,
    @PropertyElement(name = "nstnId2") val nstnId2: String?,
    @PropertyElement(name = "nstnOrd1") val nstnOrd1: String?,
    @PropertyElement(name = "nstnOrd2") val nstnOrd2: String?,
    @PropertyElement(name = "nstnSec1") val nstnSec1: String?,
    @PropertyElement(name = "nstnSec2") val nstnSec2: String?,
    @PropertyElement(name = "nstnSpd1") val nstnSpd1: String?,
    @PropertyElement(name = "nstnSpd2") val nstnSpd2: String?,
    @PropertyElement(name = "plainNo1") val plainNo1: String?,
    @PropertyElement(name = "plainNo2") val plainNo2: String?,
    @PropertyElement(name = "repTm1") val repTm1: String?,
    @PropertyElement(name = "rerdie_Div1") val rerdie_Div1: String?,
    @PropertyElement(name = "rerdie_Div2") val rerdie_Div2: String?,
    @PropertyElement(name = "reride_Num1") val reride_Num1: String?,
    @PropertyElement(name = "reride_Num2") val reride_Num2: String?,
    @PropertyElement(name = "routeType") val routeType: String?,
    @PropertyElement(name = "rtNm") val rtNm: String?,
    @PropertyElement(name = "sectOrd1") val sectOrd1: String?,
    @PropertyElement(name = "sectOrd2") val sectOrd2: String?,
    @PropertyElement(name = "stId") val stId: String?,
    @PropertyElement(name = "stNm") val stNm: String?,
    @PropertyElement(name = "staOrd") val staOrd: String?,
    @PropertyElement(name = "stationNm1") val stationNm1: String?,
    @PropertyElement(name = "stationNm2") val stationNm2: String?,
    @PropertyElement(name = "term") val term: String?,
    @PropertyElement(name = "traSpd1") val traSpd1: String?,
    @PropertyElement(name = "traSpd2") val traSpd2: String?,
    @PropertyElement(name = "traTime1") val traTime1: String?,
    @PropertyElement(name = "traTime2") val traTime2: String?,
    @PropertyElement(name = "vehId1") val vehId1: String?,
    @PropertyElement(name = "vehId2") val vehId2: String?,
)

// 실시간 버스 인자 데이터 클래스
data class BusParameterResponse(
    @SerializedName("routeId") val routeId: String,
    @SerializedName("nodeId") val nodeId: String,
    @SerializedName("sequence") val sequence: Int
)