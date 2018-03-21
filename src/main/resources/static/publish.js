"use strict";

function initPubRepos(list) {
	console.log(list);
	var select = $("#irs");
	select.empty();
	$.each(list, function(_, repo) {
		var option = $("<option>").attr("value", repo.display_name)
		.text(repo.display_name).data("id", repo.uuid);
		select.append(option);
	});

	$("#irs").on('change', function () {
		$("#login_email").setVal("");
		$("#irs").empty();
		//var repo_uuid = $(this).children(':selected').data("id");
		//var user_email = $("#login_email").val();
		//initCollectionList(repo_uuid, user_email);
	});
	//initCollectionList($("#irs option:first-child").data("id"),"");
	$("#loading").remove();
}

/*
function setCollectionList(list) {
	console.log(list);
	var select = $("#collections");
	select.empty();
	$.each(list, function(_, coll) {
		var option = $("<option>").attr("value", coll.foreign_collection_uuid)
		.text(coll.display_name).data("id", coll.id);
		select.append(option);
	});
}*/

function setCollectionList(collection_path, hierarchy) {
	if (hierarchy.children.length != 0) {
		var child;
		$.each(hierarchy.children, function(_, child) {
			collection_path += " -> ";
			collection_path += child.data;
			setCollectionList(collection_path, child);
		});
	} else {
		var select = $("#collections");
		var option = $("<option>").attr("value", collection_path).text(collection_path);
		select.append(option);
	}
}

/*
function initCollectionList(repo_uuid, user_email) {
	API.get("loading collections for selected repositories",
			"api/collection-list", {repo_uuid, user_email}, setCollectionList);
}*/

function validateEmail(email) {
    var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(String(email).toLowerCase());
}

function setNextButtonEnabled(enabled) {
	if (enabled) {
		$("#next_button").removeClass("disabled");
	} else {
		$("#next_button").addClass("disabled");
	}
}

function processHierarchy(hierarchy) {
	if (hierarchy=="") {
		$("#user_status").css("color","red");
		$("#user_status").text("Bad - this email does not correspond to any registered user!");		
	} else if (hierarchy.children.length == 0) {
		$("#user_status").css("color","red");
		$("#user_status").text("Bad - user is registered, but cannot submit to any collection!");
	} else {
		$("#user_status").text("Good - this email corresponds to a registered user!");
		$("#user_status").css("color","green");
		setNextButtonEnabled(true);
		$("#collections").empty();
		setCollectionList("", hierarchy);
	}
}

var timerId="";

function initPage(session) {
	API.get("loading list of confgured publication repositories",
			"/api/pubrepo-list", {}, initPubRepos);

	$('body').on('input', '#login_email', function() {
		var user_email = $("#login_email").val();
		var repo_uuid = $("#irs").children(':selected').data("id");
		clearTimeout(timerId);
		if (!validateEmail(user_email)) {
			$("#user_status").css("color","red");
			$("#user_status").text("Bad - this is not even a valid email address!");
		} else {
			$("#user_status").css("color","brown");
			$("#user_status").text("Checking ... wait a moment, please!");
			
			timerId = setTimeout(
					function() {
						var user_email = $("#login_email").val();
						var repo_uuid = $("#irs").children(':selected').data("id");

						API.get("check whether user exists on pub-repo",
							"/api/query-hierarchy", {repo_uuid, user_email}, processHierarchy);
						}, 1000 );
		}
	});
	
	// TODO move elsewhere
	$("#next_button").attr("href", "/meta.html");
}
