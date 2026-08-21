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
let itemShelfList = [];    /* 유통기한 일수. 없는 품목은 null */

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


/* 한 번에 보여줄 쪽 번호 개수 */
let pageBlock = 10;


/* 쪽 번호 버튼을 그립니다.

   쪽이 40개면 번호를 40개 다 늘어놓지 않고,
   지금 보고 있는 쪽 주변 10개만 보여줍니다.
   앞뒤로 넘어가는 버튼과 처음·끝으로 가는 버튼도 같이 붙습니다.

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

	/* 지금 쪽이 몇 번째 묶음에 있는지 계산합니다.
	   예) 10개씩 묶고 지금 23쪽이면 -> 21~30 이 보입니다 */
	let block = Math.ceil(page / pageBlock);
	let from = (block - 1) * pageBlock + 1;
	let to = from + pageBlock - 1;

	if (to > last) {
		to = last;
	}

	let html = "";

	/* 맨 앞으로 / 앞 묶음으로 */
	if (from > 1) {
		html = html + "<button class='page-btn' onclick='" + fnName + "(1)'>처음</button>";
		html = html + "<button class='page-btn' onclick='" + fnName + "(" + (from - 1) + ")'>이전</button>";
	}

	for (let i = from; i <= to; i++) {
		if (i == page) {
			html = html + "<button class='page-btn active'>" + i + "</button>";
		} else {
			html = html + "<button class='page-btn' onclick='" + fnName + "(" + i + ")'>" + i + "</button>";
		}
	}

	/* 뒤 묶음으로 / 맨 끝으로 */
	if (to < last) {
		html = html + "<button class='page-btn' onclick='" + fnName + "(" + (to + 1) + ")'>다음</button>";
		html = html + "<button class='page-btn' onclick='" + fnName + "(" + last + ")'>끝</button>";
	}

	/* 전체가 몇 쪽인지도 같이 알려 줍니다 */
	html = html + "<span class='page-info'>" + page + " / " + last + " 쪽 (전체 " + addComma(total) + "건)</span>";

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


/* ============================================================
   7. 서버에서 기준 데이터 받아오기

   loadMaster() 는 값을 직접 적어둔 것이고,
   아래는 서버에서 실제 값을 받아 같은 배열에 채웁니다.

   여러 번을 순서대로 불러야 해서, 하나가 끝나면 다음을 부르는
   방식으로 이어 놓았습니다. 전부 끝나면 afterDone() 이 실행됩니다.

     거래처 → 창고 → 구역 → 품목 → afterDone()
   ============================================================ */

/* 전부 받은 뒤에 실행할 함수를 담아 둡니다 */
let masterDone = null;


function loadMasterFromServer(afterDone) {

	masterDone = afterDone;

	/* 품목 분류는 품목 목록에서 뽑아 쓰므로 여기서 비워 둡니다 */
	categoryNames = [];

	apiGet("/api/partners?type=SUPPLIER&size=200", putSuppliers);
}


function putSuppliers(data) {

	supplierIds = [];
	supplierNames = [];

	for (let i = 0; i < data.items.length; i++) {
		supplierIds[i] = data.items[i].partnerId;
		supplierNames[i] = data.items[i].name;
	}

	apiGet("/api/partners?type=CUSTOMER&size=200", putCustomers);
}


function putCustomers(data) {

	customerIds = [];
	customerNames = [];

	for (let i = 0; i < data.items.length; i++) {
		customerIds[i] = data.items[i].partnerId;
		customerNames[i] = data.items[i].name;
	}

	apiGet("/api/warehouses?size=200", putWarehouseList);
}


function putWarehouseList(data) {

	warehouseIds = [];
	warehouseBaseNames = [];
	warehouseLocations = [];
	warehouseNames = [];

	for (let i = 0; i < data.items.length; i++) {

		warehouseIds[i] = data.items[i].warehouseId;
		warehouseBaseNames[i] = data.items[i].name;
		warehouseLocations[i] = data.items[i].location;

		/* 드롭다운에는 "대형(0)" 처럼 붙여서 보여 줍니다 */
		warehouseNames[i] = data.items[i].name + "(" + data.items[i].location + ")";
	}

	apiGet("/api/zones?size=200", putZoneList);
}


function putZoneList(data) {

	zoneIds = [];
	zoneWarehouseIds = [];
	zoneNames = [];
	zoneCapacities = [];
	zoneUsed = [];

	for (let i = 0; i < data.items.length; i++) {
		zoneIds[i] = data.items[i].zoneId;
		zoneWarehouseIds[i] = data.items[i].warehouseId;
		zoneNames[i] = data.items[i].zoneName;
		zoneCapacities[i] = data.items[i].capacity;

		/* 지금 얼마나 차 있는지는 재고를 따로 세어야 알 수 있어서
		   여기서는 0으로 두고, 필요한 화면에서 채웁니다 */
		zoneUsed[i] = 0;
	}

	/* 비활성 품목은 입고·출고·이동·반품에서 고를 수 없어야 하므로
	   사용 중인 품목만 받아옵니다 */
	apiGet("/api/items?active=true&page=1&size=200", putItemList1);
}


function putItemList1(data) {

	itemCodes = [];
	itemNameList = [];
	itemUnitList = [];
	itemCategoryList = [];
	itemShelfList = [];

	addItemList(data.items);

	/* 품목이 200개를 넘으면 다음 쪽도 받아옵니다 */
	if (data.total > 200) {
		apiGet("/api/items?active=true&page=2&size=200", putItemList2);
	} else {
		finishMaster();
	}
}


function putItemList2(data) {
	addItemList(data.items);
	finishMaster();
}


function addItemList(list) {

	for (let i = 0; i < list.length; i++) {

		let last = itemCodes.length;

		itemCodes[last] = "ITEM-" + list[i].itemId;
		itemNameList[last] = list[i].itemName;
		itemUnitList[last] = list[i].unit;
		itemCategoryList[last] = list[i].category;
		itemShelfList[last] = list[i].shelfLifeDays;

		/* 분류 목록도 같이 모읍니다 (같은 값은 한 번만) */
		let category = list[i].category;

		if (category != null && category != "") {

			let found = false;

			for (let k = 0; k < categoryNames.length; k++) {
				if (categoryNames[k] == category) {
					found = true;
				}
			}

			if (found == false) {
				categoryNames[categoryNames.length] = category;
			}
		}
	}
}


function finishMaster() {

	if (masterDone != null) {
		masterDone();
	}
}


/* 품목 이름으로 품목번호를 찾습니다. 없으면 -1
   (서버에 보낼 때는 이름이 아니라 번호가 필요합니다) */
function getItemId(name) {

	let i = findItemByName(name);

	if (i == -1) {
		return -1;
	}

	/* itemCodes 는 "ITEM-12" 모양이라 앞 5글자를 떼면 번호가 됩니다 */
	return itemCodes[i].substring(5) * 1;
}


/* ============================================================
   8. 로그아웃

   서버에 로그아웃을 알려서 세션을 없애고,
   화면에 저장해 둔 로그인 정보도 지웁니다.

   실패해도 로그인 화면으로는 보내야 하므로
   성공·실패 모두 같은 처리를 합니다.
   ============================================================ */

function logout() {

	sessionStorage.removeItem("userId");
	sessionStorage.removeItem("userName");
	sessionStorage.removeItem("userRole");

	apiSend("POST", "/api/logout", null, goLogin, goLogin);
}


function goLogin() {
	location.href = "login.html";
}


/* ============================================================
   9. 서버 메시지를 읽기 쉽게 다듬기

   서버가 보내는 알림 내용에는 개발용 표기가 섞여 있습니다.
     "품목(itemId=2) 재고가 threshold_min(4) 미만입니다 (현재 0, 폐기로 인한 감소)"

   화면에는 사람이 읽을 말만 남깁니다.
     "재고가 기준 수량 4개 미만입니다 (현재 0, 폐기로 인한 감소)"

   서버 문구가 바뀌어도 그대로 두면 원문이 나오므로 안전합니다.
   ============================================================ */


/* text 안에서 start 로 시작해 ")" 로 끝나는 부분을 통째로 지웁니다.
   예) "품목(itemId=2) 재고가..." -> "재고가..."                    */
function cutPart(text, start) {

	let i = text.indexOf(start);

	if (i < 0) {
		return text;
	}

	let j = text.indexOf(")", i);

	if (j < 0) {
		return text;
	}

	let left = text.substring(0, i);
	let right = text.substring(j + 1);

	/* 지운 자리 뒤에 빈칸이 남으면 같이 지웁니다 */
	if (right.charAt(0) == " ") {
		right = right.substring(1);
	}

	return left + right;
}


/* "threshold_min(4)" 처럼 적힌 부분을 "기준 수량 4개" 로 바꿉니다 */
function renameCount(text, word, label) {

	let start = word + "(";
	let i = text.indexOf(start);

	if (i < 0) {
		return text;
	}

	let j = text.indexOf(")", i);

	if (j < 0) {
		return text;
	}

	let number = text.substring(i + start.length, j);
	let left = text.substring(0, i);
	let right = text.substring(j + 1);

	/* "개" 뒤에는 받침이 없으므로 조사를 맞춰 줍니다.
	   (200개을 -> 200개를) */
	let first = right.charAt(0);

	if (first == "을") {
		right = "를" + right.substring(1);
	} else if (first == "은") {
		right = "는" + right.substring(1);
	} else if (first == "이") {
		right = "가" + right.substring(1);
	}

	return left + label + number + "개" + right;
}


/* 알림 내용을 사람이 읽을 말로 다듬습니다 */
function plainText(text) {

	if (text == null) {
		return "";
	}

	let result = text;

	/* 품목 번호는 화면에 품목명이 따로 나오므로 지웁니다 */
	result = cutPart(result, "품목(itemId=");
	result = cutPart(result, "(itemId=");

	/* 개발용 컬럼 이름을 우리말로 바꿉니다 */
	result = renameCount(result, "threshold_min", "기준 수량 ");
	result = renameCount(result, "capacity_max", "최대 보유량 ");

	return result;
}


/* ============================================================
   10. 오른쪽 위 로그인 안내

   로그인할 때 sessionStorage 에 담아 둔 이름과 역할을 보여 줍니다.
   화면마다 <div class="user-bar" id="userBar"></div> 를 두면
   여기서 채워 줍니다.
   ============================================================ */

function drawUserBar() {

	let area = document.querySelector("#userBar");

	if (area == null) {
		return;
	}

	let name = sessionStorage.getItem("userName");
	let role = sessionStorage.getItem("userRole");

	/* 로그인 정보가 없으면 안내만 합니다 */
	if (name == null) {
		area.innerHTML = "<span>로그인이 필요합니다</span>";
		return;
	}

	let roleText = "담당자";
	let roleClass = "user-role";

	if (role == "ADMIN") {
		roleText = "관리자";
		roleClass = "user-role admin";
	}

	let html = "";

	/* 승인 대기 건수는 관리자만 보면 되므로 자리만 먼저 만들어 둡니다 */
	html = html + "<span id='waitBadge'></span>";
	html = html + "<span class='" + roleClass + "'>" + roleText + "</span>";
	html = html + "<span><b class='user-name'>" + name + "</b> 님 환영합니다</span>";

	area.innerHTML = html;

	/* 관리자일 때만 대기 중인 승인 요청이 몇 건인지 확인합니다.
	   담당자는 승인 화면 자체를 볼 수 없어서 물어보지 않습니다. */
	if (role == "ADMIN") {
		apiGet("/api/approvals?status=대기&page=1&size=1", putWaitCount, hideWaitBadge);
	}
}


/* 대기 건수를 배지로 보여 줍니다. 누르면 승인 화면으로 갑니다. */
function putWaitCount(data) {

	let area = document.querySelector("#waitBadge");

	if (area == null) {
		return;
	}

	/* 대기 중인 게 없으면 아무것도 안 보여 줍니다 */
	if (data.total == 0) {
		area.innerHTML = "";
		return;
	}

	area.innerHTML = "<a class='wait-badge' href='approval.html'>승인 대기 "
	               + data.total + "건</a>";
}


/* 조회에 실패하면 그냥 배지를 숨깁니다 (화면이 멈추면 안 되므로) */
function hideWaitBadge(message) {

	let area = document.querySelector("#waitBadge");

	if (area != null) {
		area.innerHTML = "";
	}
}