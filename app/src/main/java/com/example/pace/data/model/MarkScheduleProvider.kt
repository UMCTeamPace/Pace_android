package com.example.pace.data.model

import com.example.pace.data.model.response.RouteApiResponse
import com.example.pace.data.model.response.RouteDetail
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.model.response.TransitDetail

object MarkScheduleProvider {
    private const val MOCK_ROUTE_JSON = """
      {
      "totalDistance": 13406,
      "totalTime": 2105,
      "arrivalTime": "2026-02-12T10:40:50",
      "departureTime": "2026-02-12T10:05:45",
      "routeDetailInfoResDTOList": [
        {
          "startLat": 37.5005419,
          "startLng": 126.8676709,
          "endLat": 37.49941,
          "endLng": 126.866922,
          "sequence": 1,
          "duration": 143,
          "distance": 142,
          "description": "동양미래대학.구로성심병원까지 도보",
          "points": "ki{cF}yieW`FtC",
          "transitDetail": null
        },
        {
          "startLat": 37.49941,
          "startLng": 126.866922,
          "endLat": 37.509196,
          "endLng": 126.888631,
          "sequence": 2,
          "duration": 359,
          "distance": 2347,
          "description": "버스 광화문행",
          "points": "ib{cFguieWBCOg@ESEOGkA?eA?[d@_E~A}LNmCIeBQeAq@aCm@{Ai@{AOa@IUqAiDoDaI{C{EwDwFoAoByBoD{@wAoAcBoBqCk@u@q@eA[a@uAeCa@s@mDyFkIsM}@uAUc@",
          "transitDetail": {
            "transitType": "BUS",
            "lineName": "600",
            "lineColor": "#374ff2",
            "stopCount": 3,
            "departureStop": "동양미래대학.구로성심병원",
            "arrivalStop": "신도림역",
            "departureTime": "2026-02-12T10:08:08",
            "arrivalTime": "2026-02-12T10:14:07",
            "shortName": "600",
            "locationLat": 37.509196,
            "locationLng": 126.888631,
            "headsign": "광화문",
            "stationPath": [
              "동양미래대학.구로성심병원",
              "구일역.제일제당",
              "신도림동.구로역",
              "신도림역"
            ]
          }
        },
        {
          "startLat": 37.509196,
          "startLng": 126.888631,
          "endLat": 37.508656,
          "endLng": 126.891114,
          "sequence": 3,
          "duration": 247,
          "distance": 246,
          "description": "신도림까지 도보",
          "points": "o_}cF}|meWBgKfBgB",
          "transitDetail": null
        },
        {
          "startLat": 37.508656,
          "startLng": 126.891114,
          "endLat": 37.476575,
          "endLng": 126.981363,
          "sequence": 4,
          "duration": 1080,
          "distance": 10598,
          "description": "지하철 202행",
          "points": "c||cFmlneW?Ad@Ub@YlAy@\\_@x@e@`BcAzAw@h@O\\KHCf@Gh@GrA@~@JfDZ`E\\xCXj@@lA@bACbAGbAK~@MrBo@r@Sn@SjBs@XK^K`AOzBQ^G~@MdAObB]~DcAnEgArA_@pA_@@?bBc@`Bc@pA_@l@Q|Aa@zDeAbBg@hBg@ZKlAe@`Ag@z@e@NMdAw@j@g@HKl@o@fA{A`A_BXo@Xi@|BmFHSb@yAZaALk@Nu@RuADo@Bg@FwEDgDBiCCy@A{@QkBiA}Fw@yDk@uCk@qCCUIYg@}Be@uAqAoDw@eB_AcBSa@Uc@e@}@a@}@sAiC_AkBi@kASg@Qk@K]Sy@O}@K}@Ec@C]E_AC_A@}@D_AH_AJaAL{@R{@Tw@Xy@LYLY^q@b@q@LOX_@^a@JMRSNMNU`@a@~BkC\\a@`@a@~@}@`C_CpBqBnBmBd@q@^w@L]L[HYJe@@IJs@HaA@k@?U?[AYAWC[A[Q_CQaC?AK}AK}AMcBo@qJ[kEM}ACqABiAFqALiAPkA^}A\\_AbCkHhCyH^{@j@aBLe@BGF[B[?AFYH{@JkAB}@XqGd@aKf@yK\\gHTcFRgEBo@Fm@^qBFWH_@VgA^wAZoAvBiIrBgH`DyKpCkJ~AyFjAcEv@iCXgAZeAfBgGjAiE\\yAL}@Hq@LiAF{ABg@?_CGaBOaBU{Ak@sDk@gD[kBQ_BQaBM{A[{DMqCMcCGiDCwB@cBBmD@s@DcABoA@k@",
          "transitDetail": {
            "transitType": "SUBWAY",
            "lineName": "2호선",
            "lineColor": "#35b12b",
            "stopCount": 8,
            "departureStop": "신도림",
            "arrivalStop": "사당",
            "departureTime": "2026-02-12T10:21:00",
            "arrivalTime": "2026-02-12T10:39:00",
            "shortName": "2호선",
            "locationLat": 37.476575,
            "locationLng": 126.981363,
            "headsign": "202",
            "stationPath": [
              "신도림",
              "대림",
              "구로디지털단지",
              "신대방",
              "신림",
              "봉천",
              "서울대입구",
              "낙성대",
              "사당"
            ]
          }
        },
        {
          "startLat": 37.476575,
          "startLng": 126.981363,
          "endLat": 37.4760891,
          "endLng": 126.9810116,
          "sequence": 5,
          "duration": 110,
          "distance": 73,
          "description": "대한민국 서울특별시 관악구 남부순환로 2082-25까지 도보",
          "points": "qsvcFo``fW|AT@n@",
          "transitDetail": null
        }
      ]
    }
    """

    fun getMockRouteApiResponse(): RouteApiResponse {
        return RouteApiResponse(
            routeApiResDtoList = listOf(
                RouteResponse(
                    totalDistance = 13406,
                    totalTime = 2105,
                    arrivalTime = "2026-04-11T23:59:00",
                    departureTime = "2026-04-11T22:59:00",
                    routeDetails = listOf(
                        RouteDetail(
                            sequence = 1,
                            startLat = 37.5005419,
                            startLng = 126.8676709,
                            endLat = 37.49941,
                            endLng = 126.866922,
                            duration = 143,
                            distance = 142,
                            description = "출발지에서 정류장까지 도보",
                            points = "ki{cF}yieW`FtC",
                            transitDetail = null
                        ),
                        RouteDetail(
                            sequence = 2,
                            startLat = 37.49941,
                            startLng = 126.866922,
                            endLat = 37.509196,
                            endLng = 126.888631,
                            duration = 359,
                            distance = 2347,
                            description = "600번 버스 탑승",
                            points = "ib{cFguieWBCOg@ESEOGkA?eA?[d@_E~A}LNmCIeBQeAq@aCm@{Ai@{AOa@IUqAiDoDaI{C{EwDwFoAoByBoD{@wAoAcBoBqCk@u@q@eA[a@uAeCa@s@mDyFkIsM}@uAUc@",
                            transitDetail = TransitDetail(
                                transitType = "BUS",
                                lineName = "600",
                                lineColor = "#374ff2",
                                stopCount = 3,
                                departureStop = "구로디지털단지역",
                                arrivalStop = "신도림역",
                                departureTime = "2026-04-10T10:08:08",
                                arrivalTime = "2026-04-10T10:14:07",
                                shortName = "600",
                                locationLat = 37.509196,
                                locationLng = 126.888631,
                                headsign = "광화문",
                                stationPath = listOf("구로디지털단지역", "대림역", "구로구청", "신도림역"),
                                upNext = null,
                                downNext = null
                            )
                        ),
                        RouteDetail(
                            sequence = 3,
                            startLat = 37.509196,
                            startLng = 126.888631,
                            endLat = 37.508656,
                            endLng = 126.891114,
                            duration = 247,
                            distance = 246,
                            description = "버스 하차 후 지하철역까지 도보",
                            points = "o_}cF}|meWBgKfBgB",
                            transitDetail = null
                        ),
                        RouteDetail(
                            sequence = 4,
                            startLat = 37.508656,
                            startLng = 126.891114,
                            endLat = 37.476575,
                            endLng = 126.981363,
                            duration = 1080,
                            distance = 10598,
                            description = "2호선 탑승",
                            points = "c||cFmlneW?Ad@Ub@YlAy@\\_@x@e@`BcAzAw@h@O\\KHCf@Gh@GrA@~@JfDZ`E\\xCXj@@lA@bACbAGbAK~@MrBo@r@Sn@SjBs@XK^K`AOzBQ^G~@MdAObB]~DcAnEgArA_@pA_@@?bBc@`Bc@pA_@l@Q|Aa@zDeAbBg@hBg@ZKlAe@`Ag@z@e@NMdAw@j@g@HKl@o@fA{A`A_BXo@Xi@|BmFHSb@yAZaALk@Nu@RuADo@Bg@FwEDgDBiCCy@A{@QkBiA}Fw@yDk@uCk@qCCUIYg@}Be@uAqAoDw@eB_AcBSa@Uc@e@}@a@}@sAiC_AkBi@kASg@Qk@K]Sy@O}@K}@Ec@C]E_AC_A@}@D_AH_AJaAL{@R{@Tw@Xy@LYLY^q@b@q@LOX_@^a@JMRSNMNU`@a@~BkC\\a@`@a@~@}@`C_CpBqBnBmBd@q@^w@L]L[HYJe@@IJs@HaA@k@?U?[AYAWC[A[Q_CQaC?AK}AK}AMcBo@qJ[kEM}ACqABiAFqALiAPkA^}A\\_AbCkHhCyH^{@j@aBLe@BGF[B[?AFYH{@JkAB}@XqGd@aKf@yK\\gHTcFRgEBo@Fm@^qBFWH_@VgA^wAZoAvBiIrBgH`DyKpCkJ~AyFjAcEv@iCXgAZeAfBgGjAiE\\yAL}@Hq@LiAF{ABg@?_CGaBOaBU{Ak@sDk@gD[kBQ_BQaBM{A[{DMqCMcCGiDCwB@cBBmD@s@DcABoA@k@",
                            transitDetail = TransitDetail(
                                transitType = "SUBWAY",
                                lineName = "2호선",
                                lineColor = "#35b12b",
                                stopCount = 8,
                                departureStop = "신도림",
                                arrivalStop = "사당",
                                departureTime = "2026-04-10T10:21:00",
                                arrivalTime = "2026-04-10T10:39:00",
                                shortName = "2호선",
                                locationLat = 37.476575,
                                locationLng = 126.981363,
                                headsign = "사당행",
                                stationPath = listOf("신도림", "대림", "구로디지털단지", "신대방", "신림", "봉천", "서울대입구", "낙성대", "사당"),
                                upNext = "서울대입구",
                                downNext = "낙성대"
                            )
                        ),
                        RouteDetail(
                            sequence = 5,
                            startLat = 37.476575,
                            startLng = 126.981363,
                            endLat = 37.4760891,
                            endLng = 126.9810116,
                            duration = 110,
                            distance = 73,
                            description = "도착지까지 도보",
                            points = "qsvcFo``fW|AT@n@",
                            transitDetail = null
                        )
                    )
                )
            )
        )
    }

}
