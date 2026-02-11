package com.example.pace.data.model

object MarkScheduleProvider {
    private const val MOCK_ROUTE_JSON = """
        {
      "totalDistance": 44209,
      "totalTime": 5644,
      "arrivalTime": "2026-02-11T00:56:32",
      "departureTime": "2026-02-10T23:22:28",
      "routeDetailInfoResDTOList": [
        {
          "startLat": 37.33,
          "startLng": 126.84,
          "endLat": 37.329447,
          "endLng": 126.838358,
          "sequence": 1,
          "duration": 158,
          "distance": 157,
          "description": "새마을금고앞까지 도보",
          "points": "o_zbF_mdeWlBfI",
          "transitDetail": null
        },
        {
          "startLat": 37.329447,
          "startLng": 126.838358,
          "endLat": 37.316289,
          "endLng": 126.838912,
          "sequence": 2,
          "duration": 341,
          "distance": 1656,
          "description": "버스 선부동차고지행",
          "points": "a|ybFwbdeW@@`DrBj@Zf@JvGVrDL@?bFT|@BrADf@@`ELdAAv@Jh@F@?dC@`CLtCJxAF@?r@@hAHrJXh@A\\EB?VINQB_@HcEFkC@g@?A",
          "transitDetail": {
            "transitType": "BUS",
            "lineName": "77",
            "lineColor": "#374ff2",
            "stopCount": 5,
            "departureStop": "새마을금고앞",
            "arrivalStop": "중앙역1번출구",
            "departureTime": "2026-02-10T23:35:06",
            "arrivalTime": "2026-02-10T23:40:47",
            "shortName": "77",
            "locationLat": 37.316289,
            "locationLng": 126.838912,
            "headsign": "선부동차고지",
            "stationPath": []
          }
        },
        {
          "startLat": 37.316289,
          "startLng": 126.838912,
          "endLat": 37.316036,
          "endLng": 126.838974,
          "sequence": 3,
          "duration": 103,
          "distance": 31,
          "description": "중앙까지 도보",
          "points": "yiwbFefdeW^WPJ",
          "transitDetail": null
        },
        {
          "startLat": 37.316036,
          "startLng": 126.838974,
          "endLat": 37.560974,
          "endLng": 126.986481,
          "sequence": 4,
          "duration": 3960,
          "distance": 42035,
          "description": "지하철 419행",
          "points": "ghwbFqfdeWDmA@kAVqK`@kQR{CPkBP{AV}AViAb@uAv@kB|@cB|@yApAwA~DkE`E}DnCkCt@q@v@s@bBeBp@q@r@u@?At@q@r@s@`AgAx@y@`CeCjGaGhGeGp@o@f@q@Zi@Zk@j@mA^y@L_@Vw@h@kB\\wAR_BNkAFy@FgAF_B@_AD_ADyBLgDFuBDwBB{@B}@HqBZaKZcJD{GA}BCaCA_ACs@EuBE{CC_BGyF?iACwAG}CCoACiAC{AC}AAwAKaESeCYyB}AqIaCcM{@sEy@oEk@{Ce@oCqAiHuA_IcA}Fa@_CESGc@Qu@Ic@Og@Qs@KYW{@Y}@o@{Aq@{Ai@mA[m@[g@c@i@_@c@e@i@KIo@o@o@m@gB}A}AgAk@c@k@c@oDsCkA_AoA_A?AwBcBu@i@eA{@aEaDcCmBsEmDiA_AoAcA}CcCiCuBcCmBeHwFoIyGqIyG}CcC{AiAi@a@a@_@kAcAaAw@}CcCe@a@aAw@_Aw@A?kA}@oA_A{BiBkA_AgB{AgCoByAkAqAkAiAy@iA{@_Ak@y@k@i@YcB}@cAa@YIeBk@{AYm@KcCYeCSaBOm@Eg@IsEg@aLmAgIm@i@EgBUgDc@mBUwEk@oN{CmGsA_B[_B[}AY}A[sE_AaAYy@_@uAk@k@WOI[SUOi@c@wBsBq@y@o@_Ac@u@eBuDcAyB_AwBaCoFq@_Bu@}A?As@aBu@aBqGeO{@oB{@sBuB{EgAiCgAgCmCmGeAaCgAeCu@aBo@yAYm@[o@w@{AmAwC]m@e@o@USUUQMc@Y[O]IaAQc@Cg@Cc@Ag@Ac@FmAVe@RsBtAaAr@eA~@e@b@c@^g@\\sB~@g@T_Bh@kC~@wAf@wAf@w@Xw@VSHSJm@RkHpC_@Jc@ReA`@a@Nu@`@_G|Ci@\\[VYVkBdB[\\STk@n@{AbBi@Xk@RkAN_BD_BIm@Ko@Ia@Iu@SeBq@iAq@eA}@_AgAy@sAg@iAq@}AWo@Ui@Yu@MYGOs@mAs@mAu@iAu@iAU]Y]iBcCsEiGeCgDi@{@i@cAc@cAK]Se@Me@qAkFiAoE?Aa@cBc@gB_HqYeGmVgC_KsAqFa@_Ba@aBAAc@mBe@oBsB}Iy@mD}@qDoDuNc@gBOk@Qu@y@qDc@iBa@}A_@{Ak@yAi@eAm@}@q@w@OSy@u@uA}@cA_@o@Q_@G{@Oy@KyAUwAUuAQyAUoCa@oCa@eC_@cC]}Cc@wCc@gHgAkEm@y@O{@Ky@My@MmBYsBW}Ba@_C[UGcAIe@GsAWkBo@{HiCsHiCy@WgAi@}@g@w@c@gAq@w@m@g@_@q@o@_Ay@u@s@eBiBuByBaBcBeBkBiEuEwAyAy@}@u@y@gAmAmBwBgAgAeAiAq@q@q@s@e@g@u@u@q@q@o@o@k@m@{AaByEcEkC}BcA}@iA_AeA{@SUe@i@OQOUUYOYQ[OYo@uAQc@M_@ISIUOo@Ms@UkAKo@Gm@Gq@Cq@As@?[?gD?yA@cG?qDAeB?{@Cs@I{@Iu@SaAOe@K_@Sa@O[Yg@OSOU]_@g@e@cBkAqAq@qAu@A?CAa@Ug@WmAs@uAy@m@]}@g@sBeAYOQGs@S[EWGQCa@Cu@CqABU@mBLyE\\}Hh@cAHc@Da@Fc@HA?]J]LaAf@g@Ze@Za@Vc@VsBrAyExC{E|CkD~BgBfAcBjAmEzCoAx@e@d@QRQPOVQTc@n@k@hAa@r@]p@mAxC_CrFmBxDoC~EiAhBiCbEmBnBwB|BeAdAs@n@_BzAuAbA_@Xk@\\kAt@{CdBiHvDuBfAoBfAkB`AuAt@mAj@s@^u@\\y@^q@ZuE|B}@d@]TMHOHiBjAk@f@SPg@l@k@p@g@l@GLMJgA|A}@fAk@t@i@b@u@r@iAz@u@f@}@f@s@^WL[NgAf@kClAqCnAoAb@qAd@iBb@oARcBNa@Ba@B}@@eAAeACA?iACkACwCEiFMmDGsDI}IOqACsAEgDEoCGgEKqAE]A_@Ag@AWC_@Aa@CeAGqBK{@M{@M{@KgAGwCGsGMmBCkB?mBF{DLqADuAF}ADo@Cm@Eq@K[GKAgA[_AWgA[k@Mg@I[C[Co@?s@?g@Bc@@o@Ha@P]NYR[VWZc@j@c@h@c@p@m@x@c@h@Y^URWTYZa@^MJy@`@YNa@Lc@JA@KDKB{AVgAFE@K?[?MAQAa@Cs@MeCa@_F}@mLqB}HsAcIqA}PyCqDm@kAM_AIU?oEEyA?cBAe@By@Ly@XWPcAdAg@z@i@nAa@pAmCvMaCfLu@~Cs@xCa@jB_@fBYlAc@nBCTSvAe@hDQnA]`DOlAYpAk@`BoCxFwBlEc@t@s@x@_@X[R]P_@Lc@J[Fk@Bc@?c@Ci@GgA]aAk@cCaCeAaAeAeAg@e@y@u@o@m@{AyAuHaHkH_HiBcBkBeBw@u@kBcBm@k@{@i@e@O[AYBwKjBkSfDoKhB}AV{ATA?c@Jg@JyAHiBFq@Bk@?aHLaCBs@@}@AoBGqAQuBYuBY{AUkCi@[IiBUuAGa@Ac@A_DN_AB_@C_@IYKa@Qo@[u@c@SQSY_@uAsBiH_CqI[eAS_@Q[Wa@[c@_@g@a@c@a@a@?ASWSYUc@OWKUOe@Mk@e@wBa@kBQu@_@gAo@sAi@kAK]I]Io@I}BIsBKuCUyDUeD?C@?",
          "transitDetail": {
            "transitType": "SUBWAY",
            "lineName": "4호선",
            "lineColor": "#1e97db",
            "stopCount": 26,
            "departureStop": "중앙",
            "arrivalStop": "명동",
            "departureTime": "2026-02-10T23:45:00",
            "arrivalTime": "2026-02-11T00:51:00",
            "shortName": "4호선",
            "locationLat": 37.560974,
            "locationLng": 126.986481,
            "headsign": "419",
            "stationPath": [
              "중앙",
              "한대앞",
              "상록수",
              "반월",
              "대야미",
              "수리산",
              "산본",
              "금정",
              "범계",
              "평촌",
              "인덕원",
              "정부과천청사",
              "과천",
              "대공원",
              "경마공원",
              "선바위",
              "남태령",
              "사당",
              "총신대입구",
              "동작",
              "이촌",
              "신용산",
              "삼각지",
              "숙대입구",
              "서울역",
              "회현",
              "명동"
            ]
          }
        },
        {
          "startLat": 37.560974,
          "startLng": 126.986481,
          "endLat": 37.56,
          "endLng": 126.99,
          "sequence": 5,
          "duration": 332,
          "distance": 330,
          "description": "대한민국 서울특별시 중구 예장동 3-29까지 도보",
          "points": "acgdFo`afWL{CrDcP",
          "transitDetail": null
        }
      ]
    }
    """

    fun getMockSchedule(): Schedule {
        return Schedule(
            id = 20260211L,
            title = "칭구 만나기",
            startDate = "2026-02-10",
            endDate = "2026-02-10",
            startTime = "13:20",
            endTime = "14:50",
            location = "서울특별시 중구 명동",
            memo = "루트 경로 테스트용 일정입니다.",
            calendarId = 1,
            calendarDisplayName = "내 일정",
            calendarAccountName = "user@gmail.com",
            repeatRule = null,
            withRoute = true,          // 경로 포함 여부 True
            type = "ROUTE",            // 일정 타입: ROUTE
            eventColor = android.graphics.Color.parseColor("#1e97db"), // 파란색 계열
            calendarColor = android.graphics.Color.GRAY,

            // [핵심] 여기에 JSON 문자열을 그대로 할당
            placeJson = MOCK_ROUTE_JSON,

            sourceType = "PACE"
        )
    }

}