/* ============================================================
   api.js - 서버 통신 공통 함수
   ------------------------------------------------------------
   화면(dashboard/alert/statistics/report)에서 서버를 부를 때
   매번 XMLHttpRequest를 새로 적지 않고 이 파일의 함수를 쓴다.

   사용 전 준비:
     각 html의 <head>에 아래 한 줄을 chart.js보다 먼저 넣는다.
     <script src="../js/api.js"><\/script>
   ============================================================ */


/* ------------------------------------------------------------
   1. 서버 주소 설정  ★ 여기만 고치면 전 화면에 적용됨 ★
   ------------------------------------------------------------ */

/* 톰캣 서버 주소 */
let API_HOST = "http://localhost:8080";

/* 컨텍스트 경로 (4번 담당자 확정 후 수정)
   - 확정 전에는 빈 문자열 ""  ->  http://localhost:8080/api/items
   - "/Project01" 이 붙는 경우 ->  http://localhost:8080/Project01/api/items */
let API_CONTEXT = "/dmart";


/* 경로 하나를 완전한 주소로 만들어 준다.
   makeUrl("/api/items")  ->  "http://localhost:8080/api/items"    */
function makeUrl(path) {
	return API_HOST + API_CONTEXT + path;
}


/* ------------------------------------------------------------
   2. 조회 (GET)
   ------------------------------------------------------------
   path     : "/api/alerts?resolved=false" 처럼 /api 부터 적는다
   onOk     : 성공했을 때 부를 함수. data 부분만 넘겨준다
   onFail   : 실패했을 때 부를 함수. 안 넘기면 기본 처리(경고창)

   예)
     apiGet("/api/alerts?resolved=false", putAlerts);

     function putAlerts(data) {
         let list = data.items;
         for (let i = 0; i < list.length; i++) { ... }
         drawList();
     }
   ------------------------------------------------------------ */
function apiGet(path, onOk, onFail) {
	apiSend("GET", path, null, onOk, onFail);
}


/* ------------------------------------------------------------
   3. 전송 (POST / PUT / PATCH / DELETE)
   ------------------------------------------------------------
   body : 보낼 값을 담은 객체. 없으면 null

   예)
     apiSend("PATCH", "/api/alerts/12/resolve", null, afterResolve);

     apiSend("POST", "/api/inbound",
             { itemId: 1, zoneId: 3, quantity: 100 },
             afterInbound);
   ------------------------------------------------------------ */
function apiSend(method, path, body, onOk, onFail) {

	let xhr = new XMLHttpRequest();
	xhr.open(method, makeUrl(path), true);

	/* 세션 쿠키(JSESSIONID)를 같이 보내야 로그인 상태가 유지된다.
	   이게 빠지면 로그인해도 서버가 모르는 사람으로 본다. */
	xhr.withCredentials = true;

	if (body != null) {
		xhr.setRequestHeader("Content-Type", "application/json");
	}

	xhr.onload = function () {
		apiHandle(xhr, path, onOk, onFail);
	};

	/* 서버가 아예 안 켜져 있거나 주소가 틀렸을 때 */
	xhr.onerror = function () {
		apiError(path, "서버에 연결할 수 없습니다. 톰캣이 켜져 있는지 확인하세요.", onFail);
	};

	if (body == null) {
		xhr.send();
	} else {
		xhr.send(JSON.stringify(body));
	}
}


/* ------------------------------------------------------------
   4. 응답 처리 (내부용 - 화면에서 직접 부르지 않음)
   ------------------------------------------------------------
   서버 응답은 항상 봉투 형태로 온다.
     성공 : { "success": true,  "data": { ... } }
     실패 : { "success": false, "error": { "code": "...", "message": "..." } }
   ------------------------------------------------------------ */
function apiHandle(xhr, path, onOk, onFail) {

	/* 로그인이 안 된 상태 */
	if (xhr.status == 401) {
		apiError(path, "로그인이 필요합니다.", onFail);
		return;
	}

	/* 권한이 없는 경우 (STAFF가 ADMIN 전용 기능을 부른 경우 등) */
	if (xhr.status == 403) {
		apiError(path, "권한이 없습니다.", onFail);
		return;
	}

	let res = null;

	/* 서버가 에러 페이지(HTML)를 돌려주면 JSON 변환이 실패한다.
	   경로가 틀렸을 때(404) 주로 이렇게 된다. */
	try {
		res = JSON.parse(xhr.responseText);
	} catch (e) {
		apiError(path, "서버 응답을 읽을 수 없습니다. (상태코드 " + xhr.status + ")", onFail);
		return;
	}

	if (res.success == false) {
		apiError(path, res.error.message, onFail);
		return;
	}

	onOk(res.data);
}


/* ------------------------------------------------------------
   5. 실패 처리 (내부용)
   ------------------------------------------------------------
   onFail을 넘겼으면 그 함수에 맡기고,
   안 넘겼으면 콘솔에 남기고 경고창을 띄운다.
   ------------------------------------------------------------ */
function apiError(path, message, onFail) {

	console.log("[API 실패] " + path + " : " + message);

	if (onFail == null) {
		alert(message);
	} else {
		onFail(message);
	}
}
