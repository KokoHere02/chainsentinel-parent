# ChainSentinel

ChainSentinel 鏄竴涓互 Java/Spring Boot 涓烘牳蹇冪殑閾句笂鐩戞帶涓庡憡璀﹀悗绔」鐩€? 
褰撳墠浠撳簱澶勪簬鎸佺画寮€鍙戦樁娈碉紝宸插叿澶囧彲杩愯鐨勫悗绔富閾捐矾锛屽彲鐢ㄤ簬鏈湴鑱旇皟涓庡姛鑳芥紨绀恒€?
## 褰撳墠鑳藉姏锛堥樁娈垫€э級

1. 閾鹃厤缃€佺洃鎺у湴鍧€/浠ｅ竵绠＄悊
2. 瑙勫垯绠＄悊锛堝惈妯℃澘銆佸惎鍋溿€佹潯浠舵洿鏂般€佽皟璇曞尮閰嶏級
3. 浜嬩欢涓庡憡璀︽煡璇€佸憡璀﹂噸璇?4. 浠锋牸閾捐矾锛?- HTTP 鎷変环
- OKX WS 璁㈤槄锛堥噸杩炪€侀噸璁㈤槄銆佸績璺充繚娲伙級
- WS 鏁版嵁鍐欑紦瀛?+ 寮傛鎵归噺钀藉簱 `price_tick`
- `price_tick` 鏄庣粏涓庤仛鍚堟煡璇?- `price_tick` TTL 娓呯悊浠诲姟

## 鎶€鏈爤

1. Java 17
2. Spring Boot 3.x
3. MySQL 8
4. Flyway
5. Micrometer
6. Maven 澶氭ā鍧?
## 浠撳簱缁撴瀯

1. `chainsentinel-common`: 閫氱敤缁勪欢
2. `chainsentinel-core`: 棰嗗煙妯″瀷銆佹湇鍔℃帴鍙ｃ€丏TO
3. `chainsentinel-infra`: JPA/Repository銆佷换鍔°€佽鍒欏疄鐜般€佸憡璀﹀疄鐜?4. `chainsentinel-price`: 浠锋牸鏈嶅姟涓?WS 閾捐矾
5. `chainsentinel-web`: 鍚姩妯″潡涓?REST API
6. `docs`: 璁捐鏂囨。銆佸伐浣滄€荤粨銆佽繍琛屾墜鍐?7. `http`: HTTP 鑱旇皟鑴氭湰
8. `ops`: 杩愮淮鑴氭湰涓庡垵濮嬪寲 SQL

## 蹇€熷紑濮?
## 1) 鍚姩渚濊禆

```bash
docker compose up -d
```

榛樿浼氬惎鍔細
- MySQL: `localhost:3306`
- Prometheus: `localhost:9090`

## 2) 閰嶇疆鏈湴鍙傛暟

1. 澶嶅埗绀轰緥閰嶇疆锛堣劚鏁忥級骞舵寜鏈満鏀瑰€硷細
- 鍙傝€冩枃浠讹細`chainsentinel-web/src/main/resources/application-example.yml`

2. 寤鸿涓嶈鎶婄湡瀹炲瘑閽?RPC URL 鎻愪氦鍒颁粨搴撱€?
## 3) 鍚姩鏈嶅姟

```bash
mvn -pl chainsentinel-web -am spring-boot:run
```

榛樿绔彛锛歚8080`

## API 鏂囨。涓庤仈璋?
1. API 鏂囨。锛堝綋鍓嶆帴鍙ｏ級锛歚docs/API_鎺ュ彛鏂囨。_2026-04-10.md`
2. HTTP 鑱旇皟鏂囦欢锛歚http/api.http`

## 閰嶇疆瀹夊叏璇存槑

`application-dev.yml` 鍙兘鍖呭惈鏁忔劅淇℃伅锛堝瀵嗛挜銆佺鏈?RPC锛夈€? 
瀵瑰鍙戝竷浠ｇ爜鏃惰浣跨敤绀轰緥閰嶇疆锛屼笉瑕佷笂浼犵湡瀹炲瘑閽ャ€?
## 椤圭洰鐘舵€佽鏄?
鏈」鐩粛鍦ㄨ凯浠ｄ腑锛屾帴鍙ｄ笌鏁版嵁缁撴瀯鍙兘缁х画婕旇繘銆? 
瀵瑰渚濊禆寤鸿浼樺厛鍩轰簬 `docs/API_鎺ュ彛鏂囨。_2026-04-10.md` 鍋氳仈璋冿紝骞跺叧娉ㄥ悗缁彉鏇存彁浜ゃ€?
