<%@ page language="java"
	contentType="text/html; charset=UTF-8"
	import="org.openelisglobal.common.util.StringUtil"
%>

<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>

<form id="database-cleaning-form" method="post" action="DatabaseCleaningRequest">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
<script type="text/javascript">

function /*void*/ cancel(){
	window.location.href = "MasterListsPage";
}

function /*void*/ deleteData(){
	var form = document.getElementById("database-cleaning-form");
	form.action = "DatabaseCleaningRequest";
	form.submit();
}

</script>


<div width="100%" align="center">
<h1><spring:message htmlEscape="false" code="database.clean.warning" /><br/>
<font color="red"><spring:message code="database.clean.warning.final"/></font></h1>
<input id="clean" type="button" value='<spring:message code="database.clean"/>' style="width:300px;height:50px" onclick="deleteData();"/>
<input id="cancel" type="button" value='<spring:message code="label.button.cancel"/>' style="width:300px;height:50px" onclick="cancel();" />

</div>
</form>
