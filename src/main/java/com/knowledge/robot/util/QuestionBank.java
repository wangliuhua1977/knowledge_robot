package com.knowledge.robot.util;

import java.util.*;

/**
 * 问题库（真人口吻 + 口语化生成器）
 * - 保持原 categories() 用于 UI 勾选；
 * - randomQuestion(category)：返回自然口吻的单条问题；
 * - questions(category, n)：一次生成去重后的 n 条（>=50 轻松满足）；
 * - sample50(category)：便捷方法，等价于 questions(category, 50)。
 *
 * 设计说明：
 * 1) ALL_CATEGORIES：对外暴露的全部分类（含你历史里的细分类项）；
 * 2) ALIAS：把细分类映射到“基础大类”（GEN_*），生成逻辑复用；
 * 3) SEEDS：每个基础大类放一批“手工种子问题”（更像真人）；
 * 4) 口语化生成器：结合城区/区县/场景（装维/提速/故障/停复机等）随机拼写，避免模板痕迹。
 */
public class QuestionBank {

  private static final Random RND = new Random();

  /* ===================== 1) 全部对外分类（UI 使用） ===================== */
  private static final Set<String> ALL_CATEGORIES = new LinkedHashSet<>(Arrays.asList(
          // —— 你原先的细分类（保持不变） ——
          "财务流程","IT数据需求流程","装维作业","用户套餐办理","优惠活动与营销案","宽带报障","号码携转",
          "账单与发票","增值税专票","SAP记账与税码","物料与仓储","政企业务开通","云网融合产品","IDC/机房出入",
          "账号权限开通","工单派发与考核","代维管理","装维质检","5G套餐变更","副卡/亲情网","翼支付与积分",
          "合约机与合约期","家庭网组网","公网IP与端口开放","号码销户与过户","欠费停复机","宽带提速与降档",
          "FTTR/全光组网","天翼云资源申请","大客户专线SLA","数据报送口径","用户隐私合规","异地施工协调",
          "应急通信预案","AI外呼规范",

          // —— 新增与整合类目（基础大类） ——
          "产品与资费","四川/乐山本地办理","宽带与FTTR","5G与移动业务","携转与副卡亲情网","IPTV与天翼高清",
          "天翼云与云资源","政企专线与IDC","网络与信息安全","审计与内控","IT与数据流程","工单与质检",
          "人力与激励","日常问候","打鸡血"
  ));

  /* ===================== 2) 细分类 -> 基础大类 映射 ===================== */
  private static final Map<String, String> ALIAS = new HashMap<>();
  static {
    // 细分类统一路由到基础大类（生成器在基础大类上实现）
    ALIAS.put("用户套餐办理","产品与资费");
    ALIAS.put("优惠活动与营销案","产品与资费");
    ALIAS.put("翼支付与积分","产品与资费");
    ALIAS.put("合约机与合约期","5G与移动业务");
    ALIAS.put("5G套餐变更","5G与移动业务");
    ALIAS.put("号码销户与过户","5G与移动业务");
    ALIAS.put("欠费停复机","5G与移动业务");
    ALIAS.put("号码携转","携转与副卡亲情网");
    ALIAS.put("副卡/亲情网","携转与副卡亲情网");
    ALIAS.put("宽带报障","宽带与FTTR");
    ALIAS.put("宽带提速与降档","宽带与FTTR");
    ALIAS.put("家庭网组网","宽带与FTTR");
    ALIAS.put("公网IP与端口开放","网络与信息安全");
    ALIAS.put("用户隐私合规","网络与信息安全");
    ALIAS.put("账号权限开通","IT与数据流程"); // 或“网络与信息安全”，此处更偏流程
    ALIAS.put("数据报送口径","IT与数据流程");
    ALIAS.put("装维作业","工单与质检");
    ALIAS.put("装维质检","工单与质检");
    ALIAS.put("代维管理","工单与质检");
    ALIAS.put("工单派发与考核","工单与质检");
    ALIAS.put("云网融合产品","产品与资费");
    ALIAS.put("政企业务开通","政企专线与IDC");
    ALIAS.put("大客户专线SLA","政企专线与IDC");
    ALIAS.put("IDC/机房出入","政企专线与IDC");
    ALIAS.put("应急通信预案","政企专线与IDC");
    ALIAS.put("FTTR/全光组网","宽带与FTTR");
    ALIAS.put("天翼云资源申请","天翼云与云资源");
    // 财务相关细分统一到“财务流程”
    ALIAS.put("账单与发票","财务流程");
    ALIAS.put("增值税专票","财务流程");
    ALIAS.put("SAP记账与税码","财务流程");
    ALIAS.put("物料与仓储","审计与内控");
    // 保持其它没有映射的项，默认走自身同名基础大类
  }

  /* ===================== 3) 地域 & 语料槽位（用于生成器） ===================== */
  private static final List<String> AREAS_LES = Arrays.asList(
          "市中区","沙湾区","五通桥区","金口河区","峨眉山市","犍为县","井研县","夹江县",
          "沐川县","峨边彝族自治县","马边彝族自治县"
  );
  private static final List<String> SCENARIOS = Arrays.asList(
          "装维","提速","降档","扩容","移机","故障","停复机","新装","续约","割接","抢修","优化"
  );
  private static final List<String> TIMES = Arrays.asList(
          "今天","这两天","最近一周","月底前","节前","双十一期间","寒潮这几天","台风预警期","假期前夜","周末"
  );
  private static final List<String> OPENERS = Arrays.asList(
          "麻烦问下","有个小问题","我想确认下","能不能帮我看看","同事提到","实际遇到个情况","请教一下","打扰下"
  );
  private static final List<String> ROLES = Arrays.asList(
          "客户","一线师傅","营业员","装维同事","企业IT","财务同事","网格经理","项目经理","家里老人","新入职同事"
  );
  private static final List<String> NET_BITS = Arrays.asList("300M","500M","1000M","1500M","2000M");
  private static final List<String> PRODUCTS = Arrays.asList(
          "5G通用套餐","融合宽带","天翼高清","副卡","亲情网","eSIM","国际漫游包","云主机","对象存储","政企专线"
  );
  private static final List<String> SYS_WORDS = Arrays.asList(
          "SRM","SAP","ITSM","CMDB","工单系统","计费系统","身份中心","堡垒机","日志平台","监控大屏"
  );

  /* ===================== 4) 基础大类的手工“种子问题”（更像真人） ===================== */
  private static final Map<String, List<String>> SEEDS = new HashMap<>();
  static {
    SEEDS.put("产品与资费", Arrays.asList(
            "现在电信的融合套餐怎么选更划算？有没有容易忽视的小条款？",
            "给家里老人用的，能不能只保号低月租？有推荐的组合吗？",
            "eSIM 可以直接在手表上开通吗？换机时流程复杂不？",
            "副卡和亲情网到底怎么选，费用是不是共享，一不小心会超？",
            "宽带叠加天翼高清是不是强绑定？不装会影响优惠吗？",
            "准备出国一周，国际漫游怎么开最稳妥，回来会不会忘记关？",
            "新装宽带想一步到位上千兆或更高，有没有性价比建议？",
            "套餐降档有哪些坑，合约期内能不能操作？",
            "189 App 里资费明细哪里看得最清楚？有没有“超出自动加包”？",
            "外地长期驻点，资费和归属地有没有隐形影响？"
    ));
    SEEDS.put("四川/乐山本地办理", Arrays.asList(
            "乐山城区 1000M 宽带现在下单最快多久能装？",
            "夹江老小区能否装 FTTR？如果暂不支持，有没有过渡方案？",
            "本地办副卡一定要去厅里吗？线上能不能寄到家？",
            "携号转网在乐山现在预约排期大概多久？",
            "节假日前后营业厅排队是否很长，有没有取号预约入口？",
            "本地 IPTV 老机顶盒卡顿，能申请更换吗？是否要交押金？",
            "极端天气通信保障的本地公告一般在哪看？",
            "企业专线掉线，乐山区域哪个通道响应更快？",
            "宽带提速从 300M 到 1000M，是立刻生效还是等账期？",
            "乐山本地有没有话费与翼支付积分联动活动的注意点？"
    ));
    SEEDS.put("宽带与FTTR", Arrays.asList(
            "FTTR 跟普通千兆相比，手机在家里漫游会不会更稳？",
            "老房子弱电箱太小，FTTR 光路由和分光器如何摆放？",
            "测速正常但游戏延迟高，客服一般怎么排查？我能先做什么？",
            "家里有 NAS 和摄像头，公网 IP 与端口映射怎么更安全？",
            "光猫桥接 + 自有路由跟一体机方案，稳定性与售后怎么选？",
            "IPv6 在家宽的实际好处有哪些？旧设备不支持会出问题吗？",
            "上门装维光衰这些验收指标，用户怎么对照更放心？",
            "FTTR 多节点布局怎么规划？房间多需不需要先勘察？",
            "提速包和长期带宽升级有什么区别？短期远程办公选哪个？",
            "Wi-Fi 名称与密码多久改一次较安全？访客网络怎么开？"
    ));
    SEEDS.put("5G与移动业务", Arrays.asList(
            "主副卡共享流量能不能给副卡单独设上限？超限有短信吗？",
            "异地补卡能不能办？号卡丢了怎么临时保护最稳妥？",
            "5G 消息对方不是电信号码会退回短信吗？如何计费？",
            "eSIM 和实体卡切换流程繁不繁琐？换机迁移要注意什么？",
            "异地驻点覆盖弱能否申请临时优化？需要哪些证明？",
            "流量实时刷新延迟多久算正常？账单日为何偶尔不同步？",
            "国际/港澳台漫游语音与流量怎么分别开？落地不生效咋办？",
            "套餐降档是否受合约期限制？",
            "短信/彩信的拦截与白名单在 App 哪设置？",
            "副卡是否支持未成年人，监护人流程如何？"
    ));
    SEEDS.put("携转与副卡亲情网", Arrays.asList(
            "携号转网资格校验不过常见原因有哪些？哪里能申诉？",
            "从电信转出前需要清理哪些业务？有没有清单？",
            "亲情网能否指定外省家庭成员？互打优惠还在吗？",
            "副卡是否支持异地领取开通？证件需要带哪些？",
            "携转完成后原 App/权益是否自动失效？是否需要解绑？",
            "主副卡之间流量/语音共享的限制点有哪些？",
            "亲情网短号互打现在还支持吗？外网成员有什么替代？",
            "副卡停机是否影响主卡收费？",
            "携转预约后一般多久完成？是否会中断通信？",
            "携转途中产生的费用如何计算？"
    ));
    SEEDS.put("IPTV与天翼高清", Arrays.asList(
            "机顶盒卡顿但宽带正常，多半是哪一环节的问题？",
            "天翼高清是否支持多房间同时观看？最多几个屏？",
            "点播会员和套餐内频道权益如何区分？续费是否自动？",
            "第三方盒子能否登录电信账号？会不会受限？",
            "家长控制在哪设置？能限制时长与片源分级吗？",
            "老小区线缆老化会影响 IPTV 吗？",
            "机顶盒遥控与语音助手是否有型号适配？",
            "4K 频道播放卡顿是带宽不够还是电视问题？",
            "时移/回看功能在不同套餐有区别吗？",
            "换电视后 IPTV 需要重新登记吗？"
    ));
    SEEDS.put("天翼云与云资源", Arrays.asList(
            "川内可用区和带宽计费的规则大致如何？",
            "专线打通天翼云一般走什么接入？延迟大概多少？",
            "云主机镜像加固与等保 2.0 有没有现成清单？",
            "对象存储做日志归档，跨区冗余费用与检索延时如何权衡？",
            "云上数据库主备/容灾如何在合同里约定 RPO/RTO 更稳妥？",
            "云上访问日志如何对接内部 SIEM？",
            "公网出口带宽按峰值还是 95 计费？",
            "跨账号资源共享的权限边界怎么把握？",
            "对象存储冷热分层切换策略有推荐吗？",
            "云市场第三方镜像的安全评估如何做？"
    ));
    SEEDS.put("政企专线与IDC", Arrays.asList(
            "专线 SLA 的可用性与故障响应指标业界一般怎么写？",
            "专线变更（改带宽/割接）如何提前公告？有模板吗？",
            "IDC 临时进出需要提前多久预约？是否必须双人同行？",
            "机柜温湿度告警后双方各自该做什么？",
            "突发抖动但不掉线，能否申请质量评估报告？",
            "园区割接时建议何时窗口最稳妥？",
            "上联备份链路是否能跨运营商？",
            "机房动环告警是否能短信+电话双通道？",
            "SLA 违约的赔付条款通常怎么约？",
            "专线交付后的验收要点有哪些？"
    ));
    SEEDS.put("网络与信息安全", Arrays.asList(
            "开放公网端口前最起码要做哪些安全前置？",
            "等保 2.0 常见整改项里口令复杂度与双因子如何落地？",
            "数据外发前最小化脱敏是否有统一模板？谁来审批？",
            "政企 VPN 账号最小权限如何落地？离职自动回收怎么做？",
            "家宽如何给摄像头与孩子平板隔离更安全？",
            "日志留存多少天更合规？冷/热存储如何搭配？",
            "堡垒机与审计系统怎么对接单点登录？",
            "网闸/隔离区在新老系统并存时如何布置？",
            "弱口令与默认口令巡检频率有建议吗？",
            "应急演练的剧本能否提供一个范式？"
    ));
    SEEDS.put("审计与内控", Arrays.asList(
            "内部系统权限积累多年，如何做一次“权限体检”？",
            "财务影像归档与原始单据保存年限在内控里如何约束？",
            "关键岗位离岗审计的要点有哪些？",
            "票据/合同/付款闭环如何证明可追溯？",
            "线下口头审批绕流程的整改闭环怎么写更能过审？",
            "黑白名单 + 岗位分离如何在系统里卡控？",
            "备用金/礼品卡等敏感科目如何抽查？",
            "外部审计前需要准备哪些资料目录？",
            "异常报销如何追责与回溯？",
            "内控缺陷分级标准有什么通用口径？"
    ));
    SEEDS.put("IT与数据流程", Arrays.asList(
            "小改动需不需要走变更？灰度、回退、公告怎么卡控？",
            "紧急导数是否必须脱敏？审批链通常有谁？",
            "接口联调走 VPN 还是专线？有安全评审表可参考吗？",
            "ODS 到 DWD 字段新增，元数据与血缘如何并行更新？",
            "隐私工单如何判定敏感等级？哪些场景要双人复核？",
            "API 网关的限流与熔断在高峰期如何调参？",
            "CMDB 与工单、监控如何打通防漏配？",
            "夜间发布窗口如何设置更稳？",
            "回滚预案如何做到可演练可执行？",
            "数据质量问题如何建立 owner + KPI？"
    ));
    SEEDS.put("工单与质检", Arrays.asList(
            "到场照片与光衰回填不及时会影响绩效吗？系统如何提醒？",
            "极端天气外线箱维护有无统一安全 checklist？",
            "代维考核是否有一次解决率/回单时效硬指标？",
            "派发错位或重复工单如何识别合并？",
            "抽检比例与不合格整改时限一般怎么定？",
            "紧急抢修如何记录证据链？",
            "夜间加装是否需要提前报备？",
            "师傅到场前如何确认客户在家避免扑空？",
            "超时工单自动升级的阈值设置有建议吗？",
            "投诉类工单闭环话术有范例吗？"
    ));
    SEEDS.put("财务流程", Arrays.asList(
            "差旅报销拍票影像时限有没有硬要求？过期怎么补救？",
            "供应商预付款冲销具体到哪张凭证？分别谁审核？",
            "预算已超在 SRM 里如何加签？临时额度怎么走不踩红线？",
            "固定资产跨地市调拨，需要哪些财务单据与节点？",
            "开户行变更在主数据发起后是否要上传额外证明？",
            "发票作废与红字流程如何区分场景？",
            "专票开具后发现抬头错了怎么处理？",
            "影像归档与纸质保管如何对齐？",
            "税码选择错了如何更正？",
            "跨月调账有什么注意点？"
    ));
    SEEDS.put("人力与激励", Arrays.asList(
            "一线营业员激励如何设计既拉动转化又不内卷？",
            "远程办公如何把团队目标量化到周目标？",
            "新人成长 90 天能否给一张通关卡式路线图？",
            "跨部门协作推进慢，有没有里程碑+回顾节奏模板？",
            "学习认证如何与职级评定绑定更公平？",
            "OKR 与 KPI 如何并存不打架？",
            "绩效面谈怎么更聚焦事实与数据？",
            "优秀案例复盘如何形成共享资产？",
            "轮岗与导师制如何结合？",
            "团队会议如何避免信息重复与低效？"
    ));
    SEEDS.put("日常问候", Arrays.asList(
            "早上好，今天咱们先把卡点逐个清掉，行不行？",
            "上午好，下午三点变更，提前十分钟集合对一下。",
            "中午好，补充下能量，下午清工单！",
            "傍晚了，有没收尾的活私聊我，别一个人扛。",
            "晚安，明早八点我们把跑不通的接口再过一遍。",
            "周一到了，咱们把阻塞清单缩到只剩两条如何？",
            "周五别松，最后一轮提测跑完就收官。",
            "节前自查一下安全与备份，节日安心。",
            "雨天路滑，出勤注意安全，有事在群里招呼。",
            "新人第一天，大家多带带，欢迎加入！"
    ));
    SEEDS.put("打鸡血", Arrays.asList(
            "给我一段 30 秒冲刺宣言：目标明确、节奏紧、说完就动！",
            "今天就三件事：上线、复盘、归档；不拖延不找借口。",
            "最后 1 小时倒计时，卡住就抬手，别留过夜问题！",
            "失败不可怕，怕的是踩同一个坑——复盘写到能指导新人。",
            "周一点火：路标清晰、燃料充足，直奔里程碑。",
            "月底冲 KPI：用数据说话，把指标打穿！",
            "备考节奏：方法在手、每天进步 1%，拉满！",
            "运动打卡：不求极限，只求连续，第 N 天 √。",
            "有分歧没关系，用事实拉通，我们是一队人。",
            "收官日：清单清零，文档完备，交付出彩。"
    ));
  }

  /* ===================== 5) 基础大类的生成器（城区/区县/场景/时间/对象/系统） ===================== */
  private interface Gen { String gen(); }
  private static final Map<String, Gen> GENERATORS = new HashMap<>();
  static {
    GENERATORS.put("产品与资费", () -> fmt("%s，%s在%s里看了下%s，%s这个组合现在划算吗？有没有容易忽略的小条款？",
            pick(OPENERS), pick(ROLES), pick(Arrays.asList("189 App","网上营业厅","小程序")),
            pick(PRODUCTS), pick(Arrays.asList("首月生效","次月生效","老用户升级","降档"))));

    GENERATORS.put("四川/乐山本地办理", () -> fmt("%s，乐山%s这边想办%s，%s能约上门吗？大概多久能搞定？",
            pick(OPENERS), pick(AREAS_LES), pick(Arrays.asList("1000M 宽带","FTTR 全光组网","副卡")),
            pick(TIMES)));

    GENERATORS.put("宽带与FTTR", () -> fmt("%s，家里%s带宽做%s，装维师傅一般怎么处理更稳？需要先勘察吗？",
            pick(OPENERS), pick(NET_BITS), pick(Arrays.asList("FTTR 多点覆盖","Wi-Fi 漫游优化","弱电箱改造"))));

    GENERATORS.put("5G与移动业务", () -> fmt("%s，%s里看到副卡共享流量，能不能给副卡设上限？超了会短信提醒吗？",
            pick(OPENERS), pick(Arrays.asList("套餐详情页","用量统计页","流量包管理页"))));

    GENERATORS.put("携转与副卡亲情网", () -> fmt("%s，资格校验老不过，携号转网常见卡点是什么？在乐山%s这边怎么申诉更快？",
            pick(OPENERS), pick(AREAS_LES)));

    GENERATORS.put("IPTV与天翼高清", () -> fmt("%s，天翼高清在两个房间同时看会卡吗？是带宽问题还是机顶盒型号不匹配？",
            pick(OPENERS)));

    GENERATORS.put("天翼云与云资源", () -> fmt("%s，准备上天翼云，川内可用区与带宽计费怎么选更合适？对象存储做日志归档的成本能估下吗？",
            pick(OPENERS)));

    GENERATORS.put("政企专线与IDC", () -> fmt("%s，园区夜间做%s，专线方这边提前公告与回退预案一般怎么写？有模板参考吗？",
            pick(OPENERS), pick(Arrays.asList("割接","带宽变更","链路切换"))));

    GENERATORS.put("网络与信息安全", () -> fmt("%s，想开放公网端口给内网服务，最基础的加固与暴露面清点有哪些？有没有速查表？",
            pick(OPENERS)));

    GENERATORS.put("审计与内控", () -> fmt("%s，权限积累多年很乱，想做一次“权限体检”，从哪些系统入手更有效？",
            pick(OPENERS)));

    GENERATORS.put("IT与数据流程", () -> fmt("%s，小变更要不要走流程？灰度/回退/公告三个卡点分别谁签？%s里有现成模板吗？",
            pick(OPENERS), pick(SYS_WORDS)));

    GENERATORS.put("工单与质检", () -> fmt("%s，%s出现%s，工单里怎么记录证据链？超时自动升级阈值有建议吗？",
            pick(OPENERS), pick(Arrays.asList("老小区","新装现场","极端天气")), pick(SCENARIOS)));

    GENERATORS.put("财务流程", () -> fmt("%s，%s里预算已超怎么加签？临时额度要怎么走不踩红线？",
            pick(OPENERS), pick(Arrays.asList("SRM","SAP"))));

    GENERATORS.put("人力与激励", () -> fmt("%s，一线团队怎么把周目标拆到个人不过度内卷？有没有优秀案例可复用？",
            pick(OPENERS)));

    GENERATORS.put("日常问候", () -> fmt("%s，%s先把今天的清单过一眼，下午三点前把卡点清掉，OK吗？",
            pick(Arrays.asList("早上好","上午好","中午好","下午好","傍晚好")), pick(Arrays.asList("我们","小伙伴们","各位"))));

    GENERATORS.put("打鸡血", () -> fmt("最后%s倒计时，卡住就抬手，别留过夜问题！今天目标：%s、%s、%s。",
            pick(Arrays.asList("1小时","30分钟","15分钟")), "上线", "复盘", "归档"));
  }

  /* ===================== 6) 工具方法 ===================== */
  private static String pick(List<String> list) {
    return list.get(RND.nextInt(list.size()));
  }
  private static String fmt(String t, Object... args) { return String.format(t, args); }

  private static String mapBase(String category) {
    return ALIAS.getOrDefault(category, category);
  }

  private static String generateOne(String base) {
    // 60% 用手工种子，40% 用生成器，混合更像真人
    if (SEEDS.containsKey(base) && (GENERATORS.get(base) == null || RND.nextDouble() < 0.6)) {
      List<String> list = SEEDS.get(base);
      return list.get(RND.nextInt(list.size()));
    }
    Gen g = GENERATORS.get(base);
    if (g != null) return g.gen();
    // 没有生成器时退回手工种子或通用口吻
    List<String> list = SEEDS.get(base);
    if (list != null && !list.isEmpty()) return list.get(RND.nextInt(list.size()));
    return "这块我还不熟，能先给我讲讲基本流程吗？";
  }

  /* ===================== 7) 对外 API ===================== */

  /** UI 读取分类用：保持你原先的分类 + 基础大类 */
  public static Set<String> categories() {
    return ALL_CATEGORIES;
  }

  /** 返回单条“真人口吻”的问题（满足每次都不显模板痕迹） */
  public static String randomQuestion(String category) {
    String base = mapBase(category);
    return generateOne(base);
  }

  /**
   * 一次性生成去重后的 n 条问题。
   * - 组合“手工种子 + 口语化生成器”，直到达到 n 或达到尝试上限；
   * - 典型 50~200 条毫无压力；
   * - 口语化细分：城区/区县/场景/时间/对象等随机注入。
   */
  public static List<String> questions(String category, int n) {
    String base = mapBase(category);
    LinkedHashSet<String> set = new LinkedHashSet<>();
    // 先打底：种子全加一遍
    List<String> seeds = SEEDS.get(base);
    if (seeds != null) set.addAll(seeds);
    // 再动态生成，直到 >= n 或达到尝试上限
    int tries = 0, maxTries = Math.max(n * 10, 500);
    while (set.size() < n && tries++ < maxTries) {
      set.add(generateOne(base));
      // 再注入一些“城区/区县 + 场景”变体，增强多样性
      if (RND.nextBoolean() && (GENERATORS.get(base) != null)) {
        String area = pick(AREAS_LES);
        String scn = pick(SCENARIOS);
        String t = pick(TIMES);
        set.add(String.format("乐山%s这边%s%s，有没有更稳妥的处理方式？", area, t, scn));
      }
    }
    // 如果依然不够，兜底填充口语化通用句型
    while (set.size() < n) {
      String area = pick(AREAS_LES);
      String who = pick(ROLES);
      String prod = pick(PRODUCTS);
      set.add(String.format("%s反馈在%s用%s遇到点问题，能帮忙看看吗？", who, area, prod));
    }
    return new ArrayList<>(set);
  }

  /** 便捷方法：直接拿 50 条（用于你的“至少50个问题”诉求） */
  public static List<String> sample50(String category) {
    return questions(category, 50);
  }
}
