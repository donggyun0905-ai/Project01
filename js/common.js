/* ============================================================
   common.js - 여러 화면이 같이 쓰는 함수와 데이터
   ------------------------------------------------------------
   화면마다 똑같이 적던 것들을 여기 한 곳에 모았습니다.

   사용 전 준비:
     각 html의 <head>에 아래 두 줄을 넣습니다. (순서 중요)
     <script src="../js/api.js"><\/script>
     <script src="../js/common.js"><\/script>
   ============================================================ */


/* ============================================================
   1. 화면에서 자주 쓰는 도우미 함수
   ============================================================ */


/* 1500 을 1,500 으로 바꿔 줍니다 */
function addComma(num) {

	let text = "" + num;
	let result = "";
	let count = 0;

	for (let i = text.length - 1; i >= 0; i--) {
		result = text.charAt(i) + result;
		count = count + 1;
		if (count % 3 == 0 && i > 0) {
			result = "," + result;
		}
	}
	return result;
}


/* 날짜를 2026-08-18 모양의 글자로 바꿔 줍니다 */
function getDateText(d) {

	let yyyy = d.getFullYear();
	let mm = d.getMonth() + 1;       // 달은 0부터 시작해서 1을 더해준다
	let dd = d.getDate();

	if (mm < 10) {
		mm = "0" + mm;
	}
	if (dd < 10) {
		dd = "0" + dd;
	}
	return yyyy + "-" + mm + "-" + dd;
}


/* 오늘 날짜를 글자로 돌려줍니다 */
function getToday() {
	return getDateText(new Date());
}


/* 드롭다운에서 지금 보이는 글자를 가져옵니다.
   예) getSelectText("#inPartner")  ->  "신선냉장유통(주)"     */
function getSelectText(id) {

	let box = document.querySelector(id);
	return box.options[box.selectedIndex].text;
}


/* 드롭다운에서 지금 고른 번호(value)를 가져옵니다.
   서버에 보낼 때는 이름이 아니라 이 번호가 필요합니다.   */
function getSelectValue(id) {
	return document.querySelector(id).value;
}


/* 입력칸 중에 빈 게 하나라도 있으면 true 를 돌려줍니다.
   예) if (isBlank(["#inDate", "#inCode"]) == true) { ... }   */
function isBlank(idList) {

	for (let i = 0; i < idList.length; i++) {
		if (document.querySelector(idList[i]).value == "") {
			return true;
		}
	}
	return false;
}


/* 여러 배열에서 같은 번째 값을 한꺼번에 지웁니다.
   표에서 한 줄 삭제할 때 splice 를 여러 번 적지 않아도 됩니다.
   예) removeAt([rowDates, rowCodes, rowNames], 2)            */
function removeAt(lists, index) {

	for (let i = 0; i < lists.length; i++) {
		lists[i].splice(index, 1);
	}
}


/* 여러 입력칸을 한꺼번에 비웁니다 */
function clearInput(idList) {

	for (let i = 0; i < idList.length; i++) {
		document.querySelector(idList[i]).value = "";
	}
}


/* 글자를 파일로 내려받게 합니다 (CSV 내보내기용) */
function saveFile(fileName, text) {

	let file = new Blob(["\uFEFF" + text], { type: "text/csv;charset=utf-8;" });
	let url = URL.createObjectURL(file);

	// 화면에 안 보이는 링크를 하나 만들어서 대신 눌러준다
	let link = document.createElement("a");
	link.href = url;
	link.download = fileName;
	link.click();

	URL.revokeObjectURL(url);
}


/* 모달 열기 / 닫기 (CSS의 active 클래스를 붙였다 뗍니다) */
function openModal(id) {
	document.querySelector(id).className = "modal-overlay active";
}

function closeModal(id) {
	document.querySelector(id).className = "modal-overlay";
}


/* ============================================================
   2. 여러 화면이 같이 쓰는 기준 데이터

   지금은 값을 직접 적어두었습니다.
   서버가 준비되면 loadMaster() 안쪽만 아래처럼 바꾸면
   이 파일을 쓰는 모든 화면에 한 번에 반영됩니다.

     apiGet("/api/partners?type=SUPPLIER", ...);
     apiGet("/api/warehouses", ...);
     apiGet("/api/zones", ...);
   ============================================================ */


/* 품목 분류 13종 */
let categoryNames = [];

/* 품목 목록 (입고·출고에서 이름을 골라 쓰기 위한 것) */
let itemCodes = [];
let itemNameList = [];
let itemUnitList = [];
let itemCategoryList = [];

/* 공급처 (PARTNER 중 type=SUPPLIER) - 입고에서 사용 */
let supplierIds = [];
let supplierNames = [];

/* 거래처 (PARTNER 중 type=CUSTOMER) - 출고에서 사용 */
let customerIds = [];
let customerNames = [];

/* 창고 10개 */
let warehouseIds = [];
let warehouseNames = [];        /* 화면에 보여줄 이름 (예: 대형(0)) */
let warehouseBaseNames = [];    /* 창고명만 (예: 대형) */
let warehouseLocations = [];    /* 위치만 (예: 0) */

/* 구역 30개 (창고 1곳당 PALLET / BOX / EA 3개씩) */
let zoneIds = [];
let zoneWarehouseIds = [];
let zoneNames = [];
let zoneCapacities = [];   /* 구역이 담을 수 있는 최대 수량 */
let zoneUsed = [];         /* 지금 들어 있는 수량 */


function loadMaster() {

	categoryNames = ["냉장식품", "냉동식품", "신선식품", "유제품", "베이커리",
	                 "음료", "생활용품", "청소용품", "주방잡화", "문구용품",
	                 "완구", "의류잡화", "전자소모품"];

	supplierIds   = [1, 2, 3, 4, 5, 6];
	supplierNames = ["신선냉장유통(주)", "한아름냉동식품(주)", "미소베이커리원료(주)",
	                 "생활공감유통(주)", "오피스원 문구주방(주)", "키즈앤테크(주)"];

	customerIds   = [7, 8, 9, 10, 11, 12, 13, 14, 15, 16];
	customerNames = ["이마트 성수점", "롯데마트 잠실점", "홈플러스 강서점", "GS25 물류센터",
	                 "CU 편의점 유통", "쿠팡 풀필먼트", "마켓컬리", "세븐일레븐 유통",
	                 "노브랜드 화곡점", "코스트코 양재점"];

	/* 품목. 실제로는 250개가 있고 아래는 그중 일부입니다.
	   나중에 : apiGet("/api/items?size=200", putItems) 로 받아오면
	           아래 네 줄만 바뀌고 나머지는 그대로 씁니다. */
	itemCodes        = ["ITEM-1", "ITEM-2", "ITEM-3", "ITEM-4", "ITEM-5", "ITEM-201", "ITEM-202"];
	itemNameList     = ["두부 프리미엄", "어묵 정품", "만두 프리미엄", "김치 실속형",
	                    "샐러드 프리미엄", "주방세제 대용량", "물티슈 캡형"];
	itemUnitList     = ["EA", "BOX", "BOX", "BOX", "EA", "EA", "BOX"];
	itemCategoryList = ["냉장식품", "냉동식품", "냉동식품", "냉장식품",
	                    "신선식품", "청소용품", "생활용품"];

	warehouseIds       = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
	warehouseBaseNames = ["대형", "대형", "대형", "대형", "대형",
	                      "중형", "중형", "중형", "소형", "소형"];
	warehouseLocations = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];

	/* 드롭다운에는 "대형(0)" 처럼 붙여서 보여 줍니다 */
	warehouseNames = [];

	for (let i = 0; i < warehouseIds.length; i++) {
		warehouseNames[i] = warehouseBaseNames[i] + "(" + warehouseLocations[i] + ")";
	}

	/* 구역은 규칙이 일정해서 반복문으로 만듭니다.
	   zone_id 1~30, 창고마다 PALLET / BOX / EA 순서 */
	zoneIds = [];
	zoneWarehouseIds = [];
	zoneNames = [];
	zoneCapacities = [];
	zoneUsed = [];

	/* 구역 이름은 품목 단위와 같아야 해서 PALLET / BOX / EA 로 나눕니다.
	   용량은 창고 크기에 따라 다릅니다 (seed_warehouse_zone.sql 기준)
	     대형(창고 1~5) : 120 / 300 / 1800
	     중형(창고 6~8) : 80 / 200 / 1200
	     소형(창고 9~10): 40 / 100 / 600                              */
	let names = ["PALLET", "BOX", "EA"];
	let bigCaps = [120, 300, 1800];
	let midCaps = [80, 200, 1200];
	let smallCaps = [40, 100, 600];
	let no = 1;

	for (let w = 1; w <= 10; w++) {

		let caps = bigCaps;

		if (w >= 6 && w <= 8) {
			caps = midCaps;
		} else if (w >= 9) {
			caps = smallCaps;
		}

		/* 창고마다 채워진 정도를 다르게 둬서 화면이 단조롭지 않게 합니다 */
		let rate = [0.92, 0.70, 0.35];

		if (w % 3 == 2) {
			rate = [0.45, 0.30, 0.60];
		} else if (w % 3 == 0) {
			rate = [0.25, 0.55, 0.15];
		}

		for (let k = 0; k < 3; k++) {
			zoneIds[zoneIds.length] = no;
			zoneWarehouseIds[zoneWarehouseIds.length] = w;
			zoneNames[zoneNames.length] = names[k];
			zoneCapacities[zoneCapacities.length] = caps[k];

			/* 지금 들어 있는 수량. 나중에 /api/stock-lots 합계로 바뀝니다.
			   지금은 창고마다 다르게 차 있는 것으로 둡니다. */
			zoneUsed[zoneUsed.length] = Math.round(caps[k] * rate[k]);

			no = no + 1;
		}
	}
}


/* 구역에 이만큼 더 넣을 수 있는지 확인합니다.
   넣을 수 있으면 빈 글자를, 넘치면 안내문을 돌려줍니다.
   서버(InboundService / TransferService)도 같은 검사를 합니다.
   나중에 : 실제 사용량은 /api/stock-lots 합계로 계산하면 됩니다. */
function checkZoneRoom(zoneId, addQty) {

	for (let i = 0; i < zoneIds.length; i++) {

		if (zoneIds[i] != zoneId) {
			continue;
		}

		let after = zoneUsed[i] + addQty;

		if (after > zoneCapacities[i]) {
			let room = zoneCapacities[i] - zoneUsed[i];
			return "이 구역은 최대 " + zoneCapacities[i] + "개까지 담을 수 있는데 지금 "
			     + zoneUsed[i] + "개가 들어 있습니다.\n더 넣을 수 있는 양은 " + room + "개입니다.";
		}

		return "";
	}

	return "";
}


/* 구역에 실제로 넣거나 뺀 만큼 사용량을 고쳐 둡니다 */
function changeZoneUsed(zoneId, diff) {

	for (let i = 0; i < zoneIds.length; i++) {
		if (zoneIds[i] == zoneId) {
			zoneUsed[i] = zoneUsed[i] + diff;
			return;
		}
	}
}


/* ============================================================
   3. 드롭다운 채우기
   ============================================================ */


/* 카테고리 드롭다운 */
function fillCategory(selectId) {

	let html = "";
	for (let i = 0; i < categoryNames.length; i++) {
		html = html + "<option>" + categoryNames[i] + "</option>";
	}
	document.querySelector(selectId).innerHTML = html;
}


/* 거래처 드롭다운.
   type 자리에 "SUPPLIER"(입고) 또는 "CUSTOMER"(출고)를 적습니다.
   입고에 고객을, 출고에 공급처를 넣으면 서버가 막기 때문에 구분해서 씁니다. */
function fillPartner(selectId, type) {

	let ids = supplierIds;
	let names = supplierNames;

	if (type == "CUSTOMER") {
		ids = customerIds;
		names = customerNames;
	}

	let html = "";
	for (let i = 0; i < ids.length; i++) {
		html = html + "<option value='" + ids[i] + "'>" + names[i] + "</option>";
	}
	document.querySelector(selectId).innerHTML = html;
}


/* 창고 드롭다운 */
function fillWarehouse(selectId) {

	let html = "";
	for (let i = 0; i < warehouseIds.length; i++) {
		html = html + "<option value='" + warehouseIds[i] + "'>" + warehouseNames[i] + "</option>";
	}
	document.querySelector(selectId).innerHTML = html;
}


/* 구역 드롭다운.
   고른 창고에 속한 구역만 보여 줍니다.
   창고 드롭다운의 onchange 에서 다시 불러 주면 됩니다. */
function fillZone(warehouseSelectId, zoneSelectId) {

	let picked = document.querySelector(warehouseSelectId).value;
	let html = "";

	for (let i = 0; i < zoneIds.length; i++) {
		if (zoneWarehouseIds[i] == picked) {
			html = html + "<option value='" + zoneIds[i] + "'>" + zoneNames[i] + "</option>";
		}
	}
	document.querySelector(zoneSelectId).innerHTML = html;
}


/* ============================================================
   4. 페이지 나누기

   목록이 길어지면 한 번에 다 보여주지 않고 나눠서 봅니다.
   서버도 page / size 로 나눠 주기 때문에(API 명세 1.6)
   나중에 서버 연동 시 같은 방식으로 이어집니다.
   ============================================================ */

/* 한 쪽에 몇 줄씩 보여줄지 */
let pageSize = 10;


/* 지금 쪽에 해당하는 줄 번호만 잘라 냅니다.
   list  : 전체 줄 번호 배열
   page  : 지금 보고 있는 쪽 (1부터)                            */
function cutPage(list, page) {

	let from = (page - 1) * pageSize;
	let result = [];

	for (let i = from; i < from + pageSize; i++) {
		if (i < list.length) {
			result[result.length] = list[i];
		}
	}

	return result;
}


/* 쪽 번호 버튼을 그립니다.
   areaId  : 버튼을 넣을 자리
   total   : 전체 줄 수
   page    : 지금 쪽
   fnName  : 번호를 눌렀을 때 부를 함수 이름 (예: "goPage")      */
function drawPaging(areaId, total, page, fnName) {

	let area = document.querySelector(areaId);

	if (area == null) {
		return;
	}

	let last = Math.ceil(total / pageSize);

	if (last <= 1) {
		area.innerHTML = "";
		return;
	}

	let html = "";

	for (let i = 1; i <= last; i++) {
		if (i == page) {
			html = html + "<button class='page-btn active'>" + i + "</button>";
		} else {
			html = html + "<button class='page-btn' onclick='" + fnName + "(" + i + ")'>" + i + "</button>";
		}
	}

	area.innerHTML = html;
}


/* ============================================================
   5. 품목 이름 자동완성

   입력칸에 글자를 치면 아래에 후보가 뜨고, 하나를 고르면
   품목 코드·단위·카테고리가 알아서 채워집니다.
   품목이 250개라 손으로 코드를 외워 치지 않아도 되게 한 것입니다.
   ============================================================ */


/* 후보 목록을 채웁니다. <datalist> 자리에 넣습니다. */
function fillItemList(listId) {

	let html = "";

	for (let i = 0; i < itemNameList.length; i++) {
		html = html + "<option value='" + itemNameList[i] + "'>";
	}

	document.querySelector(listId).innerHTML = html;
}


/* 품목명으로 몇 번째 품목인지 찾습니다. 없으면 -1 */
function findItemByName(name) {

	for (let i = 0; i < itemNameList.length; i++) {
		if (itemNameList[i] == name) {
			return i;
		}
	}
	return -1;
}


/* ============================================================
   6. 유통기한 남은 날짜

   오늘부터 유통기한까지 며칠 남았는지 셉니다.
   유통기한이 없으면 아주 큰 수를 돌려줘서 "임박 아님"으로 봅니다.
   ============================================================ */

function getLeftDays(expiry) {

	if (expiry == "" || expiry == null) {
		return 9999;
	}

	let today = new Date();
	let target = new Date(expiry);

	/* 밀리초 차이를 하루(1000 * 60 * 60 * 24) 로 나눕니다 */
	let gap = target.getTime() - today.getTime();

	return Math.ceil(gap / (1000 * 60 * 60 * 24));
}


/* 유통기한이 7일 이내로 남았는지 (명세서 F-05) */
function isNearExpiry(expiry) {

	let left = getLeftDays(expiry);

	if (left <= 7) {
		return true;
	}
	return false;
}