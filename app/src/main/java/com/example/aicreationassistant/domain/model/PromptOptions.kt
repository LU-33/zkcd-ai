package com.example.aicreationassistant.domain.model

/** 热门标签 */
data class HotTag(val label: String, val keyword: String)

/** 发布平台 — 仅朋友圈文案 */
enum class Platform(val label: String, val promptHint: String) {
    MOMENTS("朋友圈", "微信朋友圈文案：第一人称口语化叙述，像朋友间聊天分享，50-200字，适当换行和emoji，文末可加2-3个话题标签"),
    XIAOHONGSHU("小红书", "小红书种草文案：标题+正文+互动引导格式，多用emoji分段，100-300字，语气真诚有代入感，文末加#话题标签和互动提问（如「你们觉得呢？」）"),
    DOUYIN("抖音", "抖音短视频文案：开头用钩子吸引注意，简洁有力30-80字，口语化有网感，结尾引导点赞/评论/关注"),
    WEIBO("微博", "微博动态文案：#话题#开头，风格多元可追热点，50-150字，可@好友，适合开放式讨论和互动")
}

/** 销售渠道 — 仅商品描述 */
enum class SalesChannel(val label: String, val promptHint: String) {
    TAOBAO("淘宝", "适合淘宝商品详情页，突出卖点和优惠"),
    JD("京东", "适合京东平台，强调品质、服务和物流"),
    PINDUODUO("拼多多", "适合拼多多平台，强调性价比和拼团优惠"),
    DOUYIN_SHOP("抖音小店", "适合抖音带货风格，口语化、有感染力"),
    XIANYU("闲鱼", "适合闲鱼二手转卖，朴实真诚、如实描述")
}

data class ToneOption(val label: String, val key: String, val promptHint: String)
data class StyleOption(val label: String, val key: String, val promptHint: String)

// ==================== 朋友圈文案 选项 ====================

fun getHotTagsForSocialMedia(): List<HotTag> = listOf(
    HotTag("🌞 早安文案", "早安"), HotTag("🌙 晚安文案", "晚安"),
    HotTag("💪 励志文案", "励志正能量"), HotTag("📢 销售推广", "产品推广"),
    HotTag("📱 日常随拍", "日常生活分享"), HotTag("🎂 生日祝福", "生日"),
    HotTag("🐶 宠物日常", "宠物"), HotTag("💼 工作日常", "工作"),
    HotTag("📖 学习打卡", "学习打卡"), HotTag("🍜 美食分享", "美食"),
    HotTag("✈️ 旅行记录", "旅行"), HotTag("🏋️ 健身打卡", "健身"),
    HotTag("👗 穿搭分享", "穿搭"), HotTag("💕 情感语录", "情感"),
    HotTag("😂 搞笑日常", "搞笑"), HotTag("🎵 音乐分享", "音乐"),
    HotTag("🎬 电影推荐", "电影"), HotTag("📸 摄影作品", "摄影")
)

fun getToneOptionsForSocialMedia(): List<ToneOption> = listOf(
    ToneOption("通用", "general", "自然流畅、像朋友聊天一样，不使用刻意修饰"),
    ToneOption("幽默", "humor", "风趣幽默风格：使用反转、夸张、谐音梗制造笑点，让人读完全心一笑。适当使用😂🤣等表情"),
    ToneOption("可爱", "cute", "软萌可爱风格：使用叠词（好好看、超可爱哒）、语气词（呀、呢、哦、叭），大量emoji点缀，元气满满"),
    ToneOption("文艺", "literary", "文艺清新风格：使用诗意比喻和意象描写（微风、夕阳、光影），句式优美有节奏感，适当留白让人回味"),
    ToneOption("温柔", "gentle", "温柔细腻风格：语气轻柔缓和，用词温暖治愈，每句话像「拥抱」一样舒服，适合深夜阅读"),
    ToneOption("霸气", "bold", "霸气自信风格：短句有力、气场全开，使用果断坚定的措辞，多用感叹号收尾，不拖泥带水"),
    ToneOption("治愈", "healing", "治愈温暖风格：用具体的生活细节传递安全感（一杯热茶、一缕阳光），文字像朋友在你身边轻声安慰"),
    ToneOption("伤感", "melancholy", "淡淡伤感的走心风格：不矫情不狗血，用克制的文字表达细腻情绪，让读者产生「这就是我」的共鸣"),
    ToneOption("正式", "formal", "正式得体风格：用词规范庄重，结构清晰完整，适合工作相关和个人成就分享")
)

fun getStyleOptionsForSocialMedia(): List<StyleOption> = listOf(
    StyleOption("鲁迅风", "luxun", "鲁迅先生风格：冷峻犀利的短句+反讽语气。标志词：大抵、罢了、竟、大约的确。如「我大抵是倦了，横竖都提不起精神」"),
    StyleOption("甄嬛体", "zhenhuan", "甄嬛传台词风格：古风敬语+婉转句式。标志词：本宫/臣妾、想来、极好的、真真儿的、倒也不负恩泽"),
    StyleOption("网络热梗", "meme", "融入2024-2025网络流行语和热梗（如「i人e人」「遥遥领先」「命运的齿轮开始转动」），让文案有网感"),
    StyleOption("东北话", "dongbei", "东北方言：直爽接地气。标志词：嘎嘎、贼、咋整、老+形容词（老好了）、可劲儿。例句：「这玩意儿嘎嘎好使！」"),
    StyleOption("粤语", "cantonese", "夹杂粤语词汇和表达。标志词：好嘢、真系、好睇、食咗未、掂过碌蔗。语气轻松地道"),
    StyleOption("凡尔赛文学", "versailles", "凡尔赛式低调炫耀：明贬暗褒、先抑后扬。如「老公非要买这个包，我说不用，他偏不听，真拿他没办法」"),
    StyleOption("emo风", "emo", "emo文艺风格：有点丧但很真实。短句分行，句末不加标点，像歌词一样排列。如「今天的云很重 / 我也是」"),
    StyleOption("港风", "hongkong", "经典港风：复古繁体字韵味+粤语词汇混搭。如「做人呢，最緊要係開心」「你餓唔餓，我煮碗麵俾你食」"),
    StyleOption("台湾腔", "taiwan", "温柔台湾腔：软糯亲切。语气词：喔、啦、耶、欸、齁。句式偏长，尾音上扬。如「這個真的很不錯喔，你要不要試試看」"),
    StyleOption("四川话", "sichuan", "四川方言：巴适得板！标志词：巴适、安逸、咋子嘛、要得、好凶哦。语气泼辣直爽"),
    StyleOption("河南话", "henan", "河南方言：中！标志词：中、得劲儿、弄啥嘞、可中。语气朴实有力"),
    StyleOption("日语腔", "japanese", "日语翻译腔：です/ます体译成中文。标志词：的说、的说呢、真是太...了呢、请多关照。句末多用「呢」「的说」"),
    StyleOption("韩语腔", "korean", "韩剧台词风格：欧巴撒浪嘿~ 标志词：欧巴、亲故、完全、大发、真心。语气夸张感性"),
    StyleOption("文言文", "classical", "文言文风格：之乎者也矣焉哉。半文半白，简练有力。如「此事甚好，吾心甚慰」「今日之事，不可不察也」"),
    StyleOption("中二病", "chuunibyou", "中二病风格：自带BGM和特效名登场！标志词：封印、觉醒、漆黑的、禁忌之力、吾之真名。语气极具戏剧性"),
    StyleOption("英文版", "english", "直接输出英文版本，native speaker水平，可包含英文网络用语和俚语")
)

// ==================== 商品描述 选项 ====================

fun getHotTagsForProductDesc(): List<HotTag> = listOf(
    HotTag("📱 电子产品", "电子产品"), HotTag("👗 服装配饰", "服装配饰"),
    HotTag("💄 美妆护肤", "美妆护肤"), HotTag("🍜 食品饮料", "食品饮料"),
    HotTag("🏠 家居用品", "家居用品"), HotTag("⚽ 运动户外", "运动户外"),
    HotTag("👶 母婴用品", "母婴用品"), HotTag("📚 图书文具", "图书文具")
)

fun getToneOptionsForProductDesc(): List<ToneOption> = listOf(
    ToneOption("专业", "professional", "专业客观、数据说话"),
    ToneOption("活泼", "lively", "活泼有趣、吸引眼球"),
    ToneOption("高端", "premium", "高端大气、有质感"),
    ToneOption("亲民", "friendly", "亲切接地气、贴近用户"),
    ToneOption("简洁", "concise", "简洁明了、直击要点"),
    ToneOption("详细", "detailed", "详细介绍、面面俱到"),
    ToneOption("热情", "passionate", "热情洋溢、感染力强")
)

fun getStyleOptionsForProductDesc(): List<StyleOption> = listOf(
    StyleOption("小红书风", "xhs_style", "小红书种草文风，有代入感、带Emoji和标签"),
    StyleOption("直播带货", "livestream", "直播带货话术，节奏感强、有紧迫感"),
    StyleOption("品牌官方", "brand_official", "品牌官方文案，大气专业"),
    StyleOption("淘宝详情", "taobao_detail", "淘宝详情页风格，系统全面"),
    StyleOption("硬核评测", "tech_review", "硬核测评风格，参数对比+实际体验"),
    StyleOption("知乎体", "zhihu", "知乎风格，有深度、有理有据"),
    StyleOption("ins风", "instagram", "Instagram风格，视觉化、简洁有格调")
)

// ==================== 商品描述 — 设置选项 ====================

enum class TargetPlatform(val label: String, val promptHint: String) {
    TAOBAO("淘宝", "适合淘宝商品详情页的描述风格"),
    TMALL("天猫", "适合天猫旗舰店的品牌化描述"),
    JD("京东", "适合京东平台，强调品质和物流服务"),
    PINDUODUO("拼多多", "适合拼多多平台，强调性价比和拼团"),
    XIAOHONGSHU("小红书", "适合小红书种草风格的商品描述"),
    DOUYIN_MALL("抖音商城", "适合抖音带货风格，口语化有感染力")
}

enum class TargetMarket(val label: String, val promptHint: String) {
    CHINA("中国", "面向中国消费者，使用中文电商习惯表达"),
    USA("美国", "面向美国市场，符合Amazon等平台风格"),
    EUROPE("欧洲", "面向欧洲市场，注重品质和环保理念"),
    SOUTHEAST_ASIA("东南亚", "面向东南亚市场，适合Shopee/Lazada风格")
}

enum class OutputLanguage(val label: String, val promptHint: String) {
    CHINESE("中文", "使用简体中文输出"),
    ENGLISH("英文", "使用English输出"),
    JAPANESE("日文", "使用日本語で出力"),
    KOREAN("韩文", "한국어로 출력")
}
