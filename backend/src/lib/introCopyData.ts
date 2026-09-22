export interface IntroCopyItem {
  id: number;
  category: string;
  badge: string;
  titleLead: string;
  titleHighlight: string;
  titleTail: string;
  subtitle: string;
  tags: string[];
  ctaHint?: string;
}

export const INTRO_COPY_LIST: IntroCopyItem[] = [
  {
    id: 1,
    category: "節奏中控",
    badge: "2026 全新雙原生引擎・專為飛輪教練研發",
    titleLead: "掌控音樂節奏，",
    titleHighlight: "熱血沸騰",
    titleTail: "的飛輪課堂",
    subtitle:
      "告別手忙腳亂的紙本小抄與切歌靜音。FitnessRider 整合高音質無損變速、波形 BPM 精準對拍與大字動態倒數 HUD，新用戶享 7 天全功能免費試用，讓教練專注於帶動全場踩踏激情。",
    tags: ["0.7x ~ 1.3x 變速不變調", "1~3 秒雙軌 Crossfade", "iPad & Android 橫向車架", "100% 離線播音零延遲"],
    ctaHint: "為全場騎士注入強勁踩踏節拍",
  },
  {
    id: 2,
    category: "終結焦慮",
    badge: "課堂備課痛點終結者・10分鐘搞定45分課表",
    titleLead: "告別紙本筆記，",
    titleHighlight: "從容掌控",
    titleTail: "每場高強度挑戰",
    subtitle:
      "不必再將踏頻與秒數抄在膠帶貼在車把上。直覺可視化編輯器讓你一鍵拖曳段落、自訂衝刺區間，音樂與口令提示自動同步，備課效率提升 300%。",
    tags: ["可視化時間軸編排", "踏頻動作自動對齊", "多套課表範本套用", "課前一鍵模擬試聽"],
    ctaHint: "最聰明的備課流程，釋放教練創意",
  },
  {
    id: 3,
    category: "無損變速",
    badge: "雙原生 TimePitch 音訊技術・任何神曲都能騎",
    titleLead: "任意加速減速，",
    titleHighlight: "歌聲依然動聽",
    titleTail: "完全不變調",
    subtitle:
      "愛歌 BPM 太慢無法衝刺？太快無法爬坡？內建 0.7x 至 1.3x 連續無損音調鎖定演算法，加速節奏時鼓點鏗鏘有力，歌手人聲絕不變尖變形，完美契合踩踏踏頻。",
    tags: ["0.7x~1.3x 精細微調", "音調即時鎖定防尖銳", "雙浮點 PCM 聲學運算", "支援所有 MP3/AAC"],
    ctaHint: "打破歌曲限制，你的音樂庫就是專屬節奏庫",
  },
  {
    id: 4,
    category: "波形辨識",
    badge: "視覺化音訊波形・智慧秒測原始歌曲拍頻",
    titleLead: "音訊波形可視，",
    titleHighlight: "精準對拍",
    titleTail: "每一聲重低音鼓點",
    subtitle:
      "匯入歌曲瞬間繪製 800 點等比例音訊波形，AI 演算法秒級辨識歌曲 BPM。配合手動 Tap-Tempo 敲擊微調，讓你肉眼即可看見間奏高潮爆發點，下口令分秒不差。",
    tags: ["800 點高解析波形", "秒級自動 BPM 偵測", "手動 Tap-Tempo 校準", "音量峰值智慧可視化"],
    ctaHint: "一眼看透音樂高低潮，口令引導無懈可擊",
  },
  {
    id: 5,
    category: "車把視角",
    badge: "專為飛輪車架人體工學打造・視線不分心",
    titleLead: "橫向大字 HUD，",
    titleHighlight: "極致專注",
    titleTail: "帶動學員燃脂極限",
    subtitle:
      "置放於車把平板架上的專屬中控台。醒目大字剩餘秒數環形倒數、Zone 1~5 色塊強度即時變化，以及 5 秒提前動作預告閃爍，即使滿身大汗依然清晰可讀。",
    tags: ["超大字目標 RPM 顯示", "環形高對比倒數碼表", "下一動態預警前瞻提示", "高對比防反光配色"],
    ctaHint: "讓每一滴汗水都燃燒在正確的目標區間",
  },
  {
    id: 6,
    category: "無縫接歌",
    badge: "雙軌交錯淡入淡出・告別尷尬冷場靜音",
    titleLead: "歌曲無縫切換，",
    titleHighlight: "能量連貫",
    titleTail: "全場熱血不間斷",
    subtitle:
      "最怕爬坡結束換衝刺曲時出現數秒死寂。雙軌播放引擎在段落轉換前自動啟動 1~3 秒等能量 Crossfade，流暢平滑銜接下一首激昂曲目，全場學員心率持續沸騰。",
    tags: ["1~3秒智慧 Crossfade", "等能量平滑音量曲線", "切歌零延遲無卡頓", "雙軌獨立背景解碼"],
    ctaHint: "讓整堂飛輪課宛如頂級電音派對般一氣呵成",
  },
  {
    id: 7,
    category: "離線穩定",
    badge: "100% 設備本機快取・地下室健身房零卡頓",
    titleLead: "完全離線授課，",
    titleHighlight: "穩定可靠",
    titleTail: "無懼網路訊號斷線",
    subtitle:
      "地下重訓室、密閉飛輪教室訊號常常只剩一格甚至無服務？FitnessRider 所有音樂與課表皆儲存於設備本機，飛航模式依然百分之百流暢運作，重要課堂絕不出包。",
    tags: ["100% 離線本機運作", "零串流等待與轉圈", "飛航模式正常授課", "超低記憶體與功耗"],
    ctaHint: "教練最信賴的授課夥伴，任何場地都是你的主場",
  },
  {
    id: 8,
    category: "跨平台分享",
    badge: "iPad 與 Android 雙向互通・教練社群教案庫",
    titleLead: "跨平台一鍵導出，",
    titleHighlight: "課表流通",
    titleTail: "隨時隨地交流分享",
    subtitle:
      "採用標準開放的 workout_class.json 課表協議。教練在 iPad 上編排的經典課堂，可直接 AirDrop、LINE、雲端分享至 Android 平板，團隊師資互相代課也能即刻上手。",
    tags: ["標準 JSON 課表封裝", "iPad/Android 無縫互通", "AirDrop / LINE 傳送", "音樂路徑智慧重定位"],
    ctaHint: "打破系統藩籬，打造你的全球飛輪教案庫",
  },
  {
    id: 9,
    category: "科學訓練",
    badge: "Zone 1~5 能量分區・科學化踏頻心率對應",
    titleLead: "專業色塊分區，",
    titleHighlight: "科學化引導",
    titleTail: "訓練成效看得見",
    subtitle:
      "從 Zone 1 熱身平路到 Zone 5 極限無氧爆發，畫面以國際認可標準色彩即時提示強度。結合預估總消耗大卡與爬坡阻力標註，讓學員騎得清楚明白、越騎越有成就感。",
    tags: ["Zone 1~5 五段強度分區", "目標阻力圈數直觀標示", "累計卡路里精算模型", "站姿/坐姿動作圖示"],
    ctaHint: "用專業數據說話，建立專業教練無可取代的威信",
  },
  {
    id: 10,
    category: "雲端匯入",
    badge: "Google Drive / USB 隨身碟支援・曲目秒速入庫",
    titleLead: "自備喜愛音樂，",
    titleHighlight: "批量匯入",
    titleTail: "打造個人專屬歌單",
    subtitle:
      "支援 Google 雲端硬碟連結下載、隨身碟 OTG 或本地「下載」資料夾。一次選取 10 首乃至整張專輯，長按多選一鍵導入，音訊解碼與踏頻計算全自動完成。",
    tags: ["Google 雲端硬碟支援", "隨身碟 OTG 隨插即讀", "一鍵長按多選批次匯入", "內建安全沙盒儲存"],
    ctaHint: "海量音樂庫瞬間就緒，每次上課都是新鮮歌單",
  },
  {
    id: 11,
    category: "新手神器",
    badge: "3-2-1 智慧嗶聲語音提示・新手教練救星",
    titleLead: "智能節奏提示，",
    titleHighlight: "臨危不亂",
    titleTail: "第一次上台就上手",
    subtitle:
      "剛考完證照上台帶課手忙腳亂？系統內建倒數 3-2-1 清脆嗶聲與動作預覽，在每個段落切換前精準提醒。教練不用死背時間表，把所有熱情留給學員口令互動。",
    tags: ["段落轉換 3-2-1 倒數", "聲音與畫面同步提示", "動作要領一目了然", "降低忘詞失誤率 95%"],
    ctaHint: "從菜鳥到王牌，最給力的後盾就在車把前",
  },
  {
    id: 12,
    category: "工作室首選",
    badge: "連鎖健身房與專業飛輪館認證・教學標準化",
    titleLead: "統一教案品質，",
    titleHighlight: "專業升級",
    titleTail: "打造連鎖旗艦口碑",
    subtitle:
      "協助健身房與飛輪工作室制定一致性的課綱標準。師資團隊使用同一套系統排課與分享，替班不亂套、代課無縫銜接，顯著提升學員續課率與工作室品牌形象。",
    tags: ["連鎖團隊一致性教案", "標準化課程節奏控管", "代課替班一秒就緒", "降低新進教練培訓成本"],
    ctaHint: "頂級運動空間的共同選擇，打造高規格飛輪體驗",
  },
  {
    id: 13,
    category: "HIIT 間歇",
    badge: "極限燃脂衝刺・節奏與阻力完美呼應",
    titleLead: "高強度間歇，",
    titleHighlight: "極限爆發",
    titleTail: "帶領全場汗如雨下",
    subtitle:
      "衝刺 30 秒、間歇 15 秒？微調至千分之一秒的精確切段，搭配強烈重低音變速加速，把間歇訓練的節奏感推向極致，讓全班學員沉浸在痛快淋漓的能量釋放中。",
    tags: ["精確至毫秒段落裁切", "衝刺/平路/爬坡快速切換", "即時踏頻衝刺警示", "運動後總體能報表"],
    ctaHint: "用最極致的節奏安排，榨乾體內每一克卡路里",
  },
  {
    id: 14,
    category: "客製踏頻",
    badge: "自由配速自訂 RPM・全方位符合騎乘需求",
    titleLead: "自由定義配速，",
    titleHighlight: "隨心所欲",
    titleTail: "編排你的專屬騎行風格",
    subtitle:
      "無論是 60 RPM 沉重肌力爬坡，還是 110 RPM 高踏頻平路巡航，皆可自由微調每段落的建議速度與阻力檔位。量身打造適合初階燃脂到進階選手的專屬課表。",
    tags: ["RPM 踏頻自訂調整", "音樂倍速自動匹配", "自訂動作名稱與指令", "彈性分段任意增刪"],
    ctaHint: "每一首歌曲，都能譜出教練獨一無二的教學靈魂",
  },
  {
    id: 15,
    category: "防汗操作",
    badge: "大面積熱區高對比設計・汗水滿手也不誤觸",
    titleLead: "抗汗直覺觸控，",
    titleHighlight: "從容盲操",
    titleTail: "高強度騎行輕鬆點擊",
    subtitle:
      "騎乘時汗流浹背、視線晃動？按鈕皆經過加大人體工學佈局，支援高對比度暗色模式。無論微調速度、跳過段落或暫停，教練抬手一碰即準，授課節奏永遠不亂。",
    tags: ["超大按鍵防誤觸佈局", "深色高對比防眩光", "觸覺震動即時反饋", "防水防汗操作最佳化"],
    ctaHint: "為真實運動環境而生，最貼心的工業級介面設計",
  },
  {
    id: 16,
    category: "沉浸體驗",
    badge: "聲光節奏合一・讓每一次踩踏都充滿力量",
    titleLead: "心跳與鼓點共振，",
    titleHighlight: "沉浸騎行",
    titleTail: "忘卻疲憊超越自我",
    subtitle:
      "音樂是飛輪的靈魂。當鼓點、燈光、車把倒數與踏頻完美對齊，學員不再感覺踩踏沉重，而是跟著節奏浪潮一路狂飆。這就是 FitnessRider 帶來的非凡感染力。",
    tags: ["節奏對位強化感受", "學員專注度大幅提升", "音樂情緒起伏鋪陳", "創造回味無窮的課堂"],
    ctaHint: "讓每一堂飛輪課，成為學員一週中最期待的 45 分鐘",
  },
  {
    id: 17,
    category: "人聲鎖調",
    badge: "先進相位聲學補償・保留歌曲最純粹的情感",
    titleLead: "專業調音引擎，",
    titleHighlight: "飽滿純淨",
    titleTail: "重現錄音室級震撼音質",
    subtitle:
      "傳統變速播放軟體容易造成人聲金屬音、破音或像唐老鴨？FitnessRider 採用專業級相位聲學演算法，加速至 1.2x 依然保留人聲溫潤細節與低頻重擊感，聽覺享受不妥協。",
    tags: ["專業相位保留演算法", "低頻厚重打擊感增強", "避免數位失真與抖動", "Hi-Fi 高保真音訊輸出"],
    ctaHint: "給耳朵最挑剔的教練，最純粹的聲學饗宴",
  },
  {
    id: 18,
    category: "提升續課",
    badge: "滿班率與口碑保證・王牌飛輪教練秘密武器",
    titleLead: "課堂流暢專業，",
    titleHighlight: "口碑爆棚",
    titleTail: "堂堂客滿的必備神器",
    subtitle:
      "流暢無縫的接歌、清清楚楚的動作倒數與無比振奮的節奏帶動，讓學員每一次下課都意猶未盡。教練專業形象全面升級，續課率與私人預約自然水漲船高。",
    tags: ["學員好評度提升 98%", "課堂客滿率顯著攀升", "專業形象深植人心", "健身房爭相邀約的王牌"],
    ctaHint: "打造個人教練品牌，用專業科技成為全場焦點",
  },
  {
    id: 19,
    category: "數據分析",
    badge: "訓練結構即時試算・課後復盤一目了然",
    titleLead: "卡路里科學試算，",
    titleHighlight: "數據可循",
    titleTail: "設計最有感的燃脂曲線",
    subtitle:
      "編排課表時自動試算總時長、燃脂/爬坡/衝刺時間佔比與預估消耗卡路里。在開始上課前便能掌握課程強度波浪圖，讓每次運動成效皆有嚴謹數據支撐。",
    tags: ["預估卡路里消耗模型", "強度時間比例分佈圖", "段落踏頻熱度曲線", "課表設計平衡度評估"],
    ctaHint: "結合藝術與科學，編出最無懈可擊的黃金教案",
  },
  {
    id: 20,
    category: "專屬禮遇",
    badge: "2026 培訓推廣計畫・輸入代碼享 30 天 VIP",
    titleLead: "攜手推廣課程，",
    titleHighlight: "專屬代碼",
    titleTail: "開啟 30 天無極限創作",
    subtitle:
      "搭配 2026 年度培訓推廣課程，App 內輸入年度代碼「26FR-NR」即可從 7 天免費升級至 30 天完整 VIP 權限！無限制匯入曲目、無限編排課表，立即啟動你的飛輪革命。",
    tags: ["年度專屬代碼 26FR-NR", "享 30 天無限制 VIP 體驗", "全部功能完整開放", "支援無痛升級方案"],
    ctaHint: "立即領取專屬禮遇，與頂尖飛輪教練並肩同行",
  },
];
