package com.example.progressnest

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class Goal(val id:Long,val content:String,val priority:String,val color:Long,val done:Boolean=false)
data class SubProject(val id:Long,val name:String,val goals:List<Goal> = emptyList())
data class Project(val id:Long,val name:String,val color:Long,val goals:List<Goal> = emptyList(),val children:List<SubProject> = emptyList())
enum class ThemeChoice { SYSTEM,LIGHT,DARK }
enum class ItemKind { PROJECT,SUBPROJECT,GOAL }
data class SelectedItem(val kind:ItemKind,val projectId:Long,val subId:Long?=null,val goalId:Long?=null)
data class DragSession(val key:String,val group:String,val from:Int,val target:Int,val offset:Float,val extent:Float)

private val colors=listOf(0xFF6750A4,0xFFE05D5D,0xFF2E8B78,0xFFD78635,0xFF3679C8,0xFFB65085)
private fun Project.allGoals()=goals+children.flatMap{it.goals}
private fun progress(goals:List<Goal>)=if(goals.isEmpty())0 else goals.count{it.done}*100/goals.size

class MainActivity:ComponentActivity(){override fun onCreate(state:Bundle?){super.onCreate(state);setContent{ProgressApp(applicationContext)}}}

@Composable fun ProgressApp(context:Context){
    val store=remember{LocalStore(context)}
    var projects by remember{mutableStateOf(store.loadProjects())}
    var theme by remember{mutableStateOf(store.loadTheme())}
    val dark=when(theme){ThemeChoice.SYSTEM->isSystemInDarkTheme();ThemeChoice.LIGHT->false;ThemeChoice.DARK->true}
    val scheme=if(dark)darkColorScheme(primary=Color(0xFFD1BCFF),background=Color(0xFF141217),surface=Color(0xFF211E24))else lightColorScheme(primary=Color(0xFF6750A4),background=Color(0xFFF8F7FB),surface=Color.White)
    MaterialTheme(colorScheme=scheme){Surface(Modifier.fillMaxSize(),color=scheme.background){MainScreen(projects,{projects=it;store.saveProjects(it)},theme,{theme=it;store.saveTheme(it)})}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MainScreen(projects:List<Project>,update:(List<Project>)->Unit,theme:ThemeChoice,setTheme:(ThemeChoice)->Unit){
    var openedId by remember{mutableStateOf<Long?>(null)}
    var expanded by remember{mutableStateOf(setOf<Long>())}
    var create by remember{mutableStateOf<Pair<ItemKind,Long?>?>(null)}
    var createSubId by remember{mutableStateOf<Long?>(null)}
    var selected by remember{mutableStateOf<SelectedItem?>(null)}
    var editing by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf(false)}
    var themeDialog by remember{mutableStateOf(false)}
    val opened=projects.firstOrNull{it.id==openedId}
    BackHandler(enabled=opened!=null){openedId=null}
    Scaffold(topBar={TopAppBar(title={Column{Text(opened?.name?:"进度巢",fontWeight=FontWeight.Bold);Text(if(opened==null)"管理好每一个项目的进度" else "子项目与目标",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}},navigationIcon={if(opened!=null)IconButton(onClick={openedId=null}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},actions={IconButton(onClick={themeDialog=true}){Icon(Icons.Default.Brightness6,"主题")}})},floatingActionButton={if(opened==null)FloatingActionButton(onClick={create=ItemKind.PROJECT to null;createSubId=null}){Icon(Icons.Default.Add,"创建项目")}}){pad->
        if(opened==null)HomePage(projects,Modifier.padding(pad),{openedId=it},{selected=SelectedItem(ItemKind.PROJECT,it)}){id,step->update(projects.moveId(id,step){it.id})}
        else ProjectPage(opened,expanded,{id->expanded=if(id in expanded)expanded-id else expanded+id},{create=ItemKind.SUBPROJECT to opened.id;createSubId=null},{subId->create=ItemKind.GOAL to opened.id;createSubId=subId},{ref->selected=ref},{goalId,subId->update(projects.map{p->if(p.id!=opened.id)p else if(subId==null)p.copy(goals=p.goals.map{if(it.id==goalId)it.copy(done=!it.done)else it})else p.copy(children=p.children.map{s->if(s.id==subId)s.copy(goals=s.goals.map{if(it.id==goalId)it.copy(done=!it.done)else it})else s})})},{subId,step->update(projects.map{if(it.id==opened.id)it.copy(children=it.children.moveId(subId,step){s->s.id})else it})},{goalId,subId,step->update(projects.map{p->if(p.id!=opened.id)p else if(subId==null)p.copy(goals=p.goals.moveId(goalId,step){it.id})else p.copy(children=p.children.map{s->if(s.id==subId)s.copy(goals=s.goals.moveId(goalId,step){it.id})else s})})},Modifier.padding(pad))
    }
    create?.let{(kind,pid)->ItemEditor(kind,null,{create=null}){name,priority,color->val id=System.nanoTime();update(when(kind){ItemKind.PROJECT->listOf(Project(id,name,color))+projects;ItemKind.SUBPROJECT->projects.map{if(it.id==pid)it.copy(children=listOf(SubProject(id,name))+it.children)else it};ItemKind.GOAL->projects.map{p->if(p.id!=pid)p else{val g=Goal(id,name,priority,color);if(createSubId==null)p.copy(goals=listOf(g)+p.goals)else p.copy(children=p.children.map{if(it.id==createSubId)it.copy(goals=listOf(g)+it.goals)else it})}}});create=null}}
    selected?.let{ref->ItemActions(ref,{selected=null},{editing=true},{deleting=true})}
    if(editing)selected?.let{ref->val initial=findValues(projects,ref);ItemEditor(ref.kind,initial,{editing=false;selected=null}){name,priority,color->update(editItem(projects,ref,name,priority,color));editing=false;selected=null}}
    if(deleting)selected?.let{ref->DeleteDialog(ref.kind,{deleting=false;selected=null}){update(deleteItem(projects,ref));if(ref.kind==ItemKind.PROJECT)openedId=null;deleting=false;selected=null}}
    if(themeDialog)ThemeDialog(theme,{themeDialog=false}){setTheme(it);themeDialog=false}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun HomePage(projects:List<Project>,modifier:Modifier,onOpen:(Long)->Unit,onLong:(Long)->Unit,onMove:(Long,Int)->Unit){
 var drag by remember{mutableStateOf<DragSession?>(null)};val gap=with(LocalDensity.current){12.dp.toPx()};val goals=projects.flatMap{it.allGoals()}
 LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.fillMaxWidth().padding(22.dp)){Text("整体进度");Text("${goals.count{it.done}} / ${goals.size}",fontSize=38.sp,fontWeight=FontWeight.Bold);Text("已完成目标 · ${projects.size} 个项目正在推进")}};Text("我的项目",Modifier.padding(top=22.dp),fontSize=19.sp,fontWeight=FontWeight.Bold)};itemsIndexed(projects,key={_,p->p.id}){i,p->var h by remember(p.id){mutableIntStateOf(1)};val d=drag;val active=d?.key=="p${p.id}";val shiftTarget=if(!active&&d?.group=="projects")shift(i,d)else 0f;val animatedShift by animateFloatAsState(shiftTarget,spring(),label="p");val y=if(d==null)0f else if(active)d.offset else animatedShift;Card(Modifier.zIndex(if(active)2f else 0f).graphicsLayer{translationY=y}.onSizeChanged{h=it.height}.fillMaxWidth().combinedClickable(onClick={onOpen(p.id)},onLongClick={onLong(p.id)})){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Folder,null,tint=Color(p.color));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.children.size} 个子项目 · ${p.allGoals().size} 个目标",fontSize=12.sp);ProgressBar(progress(p.allGoals()),Color(p.color),MaterialTheme.colorScheme.surfaceVariant)};DragHandle({drag=DragSession("p${p.id}","projects",i,i,0f,h+gap)},{drag=drag?.target(it,projects.size)},{val x=drag;drag=null;if(x!=null)onMove(p.id,x.target-x.from)})}}}}
}

@Composable private fun ProjectPage(project:Project,expanded:Set<Long>,toggleExpand:(Long)->Unit,addSub:()->Unit,addGoal:(Long?)->Unit,onLong:(SelectedItem)->Unit,onToggle:(Long,Long?)->Unit,onMoveSub:(Long,Int)->Unit,onMoveGoal:(Long,Long?,Int)->Unit,modifier:Modifier){
 var drag by remember(project.id){mutableStateOf<DragSession?>(null)};val gap=with(LocalDensity.current){10.dp.toPx()}
 LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Card(colors=CardDefaults.cardColors(containerColor=Color(project.color))){Column(Modifier.fillMaxWidth().padding(20.dp)){Text("项目进度",color=Color.White);Text("${progress(project.allGoals())}%",fontSize=34.sp,color=Color.White);ProgressBar(progress(project.allGoals()),Color.White,Color.White.copy(.3f))}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("子项目",fontSize=18.sp);TextButton(addSub){Text("＋ 添加")}}};project.children.forEachIndexed{i,sub->item(key="s${sub.id}"){DragRow("s${sub.id}","subs",i,project.children.size,drag,{drag=it},{step->onMoveSub(sub.id,step)},gap){start,move,end->SubProjectBlock(sub,sub.id in expanded,{toggleExpand(sub.id)},{addGoal(sub.id)},{onLong(SelectedItem(ItemKind.SUBPROJECT,project.id,sub.id))},start,move,end)}};if(sub.id in expanded)itemsIndexed(sub.goals,key={_,g->"sg${sub.id}${g.id}"}){j,g->DragRow("g${g.id}","sub${sub.id}",j,sub.goals.size,drag,{drag=it},{step->onMoveGoal(g.id,sub.id,step)},gap,Modifier.padding(start=18.dp)){start,move,end->GoalCard(g,{onToggle(g.id,sub.id)},{onLong(SelectedItem(ItemKind.GOAL,project.id,sub.id,g.id))},start,move,end)}}};item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("直属目标",fontSize=18.sp);TextButton({addGoal(null)}){Text("＋ 添加")}}};itemsIndexed(project.goals,key={_,g->"g${g.id}"}){i,g->DragRow("g${g.id}","direct",i,project.goals.size,drag,{drag=it},{step->onMoveGoal(g.id,null,step)},gap){start,move,end->GoalCard(g,{onToggle(g.id,null)},{onLong(SelectedItem(ItemKind.GOAL,project.id,goalId=g.id))},start,move,end)}}}
}

@Composable private fun DragRow(key:String,group:String,index:Int,count:Int,drag:DragSession?,set:(DragSession?)->Unit,commit:(Int)->Unit,gap:Float,modifier:Modifier=Modifier,content: @Composable (() -> Unit, (Float) -> Unit, () -> Unit) -> Unit){var h by remember(key){mutableIntStateOf(1)};val gesture=remember(key){mutableStateOf<DragSession?>(null)};val active=drag?.key==key;val shiftTarget=if(!active&&drag?.group==group)shift(index,drag)else 0f;val animatedShift by animateFloatAsState(shiftTarget,spring(),label=key);val y=if(drag==null)0f else if(active)drag.offset else animatedShift;Box(modifier.zIndex(if(active)2f else 0f).graphicsLayer{translationY=y}.onSizeChanged{h=it.height}){content({val d=DragSession(key,group,index,index,0f,h+gap);gesture.value=d;set(d)},{dy->val d=gesture.value?.target(dy,count)?:DragSession(key,group,index,index,0f,h+gap).target(dy,count);gesture.value=d;set(d)},{val d=gesture.value;gesture.value=null;set(null);if(d!=null)commit(d.target-d.from)})}}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SubProjectBlock(s:SubProject,open:Boolean,toggle:()->Unit,add:()->Unit,long:()->Unit,start:()->Unit,move:(Float)->Unit,end:()->Unit){Card(Modifier.fillMaxWidth().combinedClickable(onClick=toggle,onLongClick=long)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(open)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null);Column(Modifier.weight(1f)){Text(s.name);Text("${s.goals.size} 个目标 · ${progress(s.goals)}%",fontSize=12.sp)};IconButton(onClick=add){Icon(Icons.Default.Add,null)};DragHandle(start,move,end)}}}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun GoalCard(g:Goal,click:()->Unit,long:()->Unit,start:()->Unit,move:(Float)->Unit,end:()->Unit){Card(Modifier.fillMaxWidth().combinedClickable(onClick=click,onLongClick=long),colors=CardDefaults.cardColors(containerColor=Color(g.color).copy(.18f))){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(g.done,{click()});Column(Modifier.weight(1f)){Text(g.content);Text("${g.priority}优先级",fontSize=12.sp)};DragHandle(start,move,end)}}}
private fun DragSession.target(dy:Float,count:Int)=copy(offset=dy,target=(from+(dy/extent).roundToInt()).coerceIn(0,count-1))
private fun shift(i:Int,d:DragSession)=when{d.target>d.from&&i in d.from+1..d.target->-d.extent;d.target<d.from&&i in d.target until d.from->d.extent;else->0f}
@Composable private fun ProgressBar(value:Int,color:Color,track:Color,modifier:Modifier=Modifier){Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(track)){Box(Modifier.fillMaxHeight().fillMaxWidth((value.coerceIn(0,100)/100f)).clip(CircleShape).background(color))}}

@Composable private fun DragHandle(start:()->Unit,move:(Float)->Unit,end:()->Unit){val startNow by rememberUpdatedState(start);val moveNow by rememberUpdatedState(move);val endNow by rememberUpdatedState(end);var active by remember{mutableStateOf(false)};Icon(Icons.Default.DragHandle,null,tint=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(40.dp).padding(8.dp).pointerInput(Unit){var dy=0f;detectVerticalDragGestures(onDragStart={dy=0f;active=true;startNow()},onDragEnd={active=false;endNow()},onDragCancel={active=false;endNow()}){c,a->c.consume();dy+=a;moveNow(dy)}})}
private fun <T> List<T>.moveId(id:Long,step:Int,key:(T)->Long):List<T>{val from=indexOfFirst{key(it)==id};if(from<0)return this;val to=(from+step).coerceIn(indices);if(from==to)return this;return toMutableList().apply{add(to,removeAt(from))}}

@Composable private fun ItemActions(ref:SelectedItem,dismiss:()->Unit,edit:()->Unit,delete:()->Unit){val transparent=ListItemDefaults.colors(containerColor=Color.Transparent);AlertDialog(onDismissRequest=dismiss,title={Text(when(ref.kind){ItemKind.PROJECT->"管理项目";ItemKind.SUBPROJECT->"管理子项目";ItemKind.GOAL->"管理目标"})},text={Column{ListItem(headlineContent={Text("修改")},leadingContent={Icon(Icons.Default.Edit,null)},colors=transparent,modifier=Modifier.clickable(onClick=edit));ListItem(headlineContent={Text("删除",color=MaterialTheme.colorScheme.error)},leadingContent={Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.error)},colors=transparent,modifier=Modifier.clickable(onClick=delete))}},confirmButton={})}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ItemEditor(kind:ItemKind,initial:Triple<String,String,Long>?,dismiss:()->Unit,save:(String,String,Long)->Unit){var name by remember{mutableStateOf(initial?.first?:"")};var priority by remember{mutableStateOf(initial?.second?:"中")};var color by remember{mutableLongStateOf(initial?.third?:colors.first())};AlertDialog(onDismissRequest=dismiss,title={Text(if(initial==null)"创建${kind.label()}" else "修改${kind.label()}")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(name,{name=it},label={Text(if(kind==ItemKind.GOAL)"目标内容" else "名称")},singleLine=true);if(kind==ItemKind.GOAL){Text("优先级",fontWeight=FontWeight.Bold);SingleChoiceSegmentedButtonRow{listOf("高","中","低").forEachIndexed{i,v->SegmentedButton(priority==v,{priority=v},SegmentedButtonDefaults.itemShape(i,3)){Text(v)}}}};if(kind!=ItemKind.SUBPROJECT){Text("颜色",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){colors.forEach{v->Box(Modifier.size(34.dp).background(Color(v),CircleShape).clickable{color=v},contentAlignment=Alignment.Center){if(color==v)Icon(Icons.Default.Check,null,tint=Color.White)}}}}}},confirmButton={Button(onClick={save(name.trim(),priority,color)},enabled=name.isNotBlank()){Text("保存")}},dismissButton={TextButton(onClick=dismiss){Text("取消")}})}

@Composable private fun DeleteDialog(kind:ItemKind,dismiss:()->Unit,confirm:()->Unit){AlertDialog(onDismissRequest=dismiss,title={Text("删除${kind.label()}？")},text={Text(if(kind==ItemKind.PROJECT)"项目中的所有子项目和目标也会被删除。" else if(kind==ItemKind.SUBPROJECT)"子项目中的所有目标也会被删除。" else "删除后无法恢复。")},confirmButton={Button(confirm,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("删除")}},dismissButton={TextButton(onClick=dismiss){Text("取消")}})}
@Composable private fun ThemeDialog(current:ThemeChoice,dismiss:()->Unit,set:(ThemeChoice)->Unit){AlertDialog(onDismissRequest=dismiss,title={Text("外观")},text={Column{ThemeChoice.entries.forEach{v->ListItem(headlineContent={Text(when(v){ThemeChoice.SYSTEM->"跟随系统";ThemeChoice.LIGHT->"浅色";ThemeChoice.DARK->"深色"})},leadingContent={RadioButton(current==v,{set(v)})})}}},confirmButton={})}
private fun ItemKind.label()=when(this){ItemKind.PROJECT->"项目";ItemKind.SUBPROJECT->"子项目";ItemKind.GOAL->"目标"}

private fun findValues(ps:List<Project>,r:SelectedItem):Triple<String,String,Long>{val p=ps.first{it.id==r.projectId};return when(r.kind){ItemKind.PROJECT->Triple(p.name,"中",p.color);ItemKind.SUBPROJECT->Triple(p.children.first{it.id==r.subId}.name,"中",colors.first());ItemKind.GOAL->{val g=if(r.subId==null)p.goals.first{it.id==r.goalId}else p.children.first{it.id==r.subId}.goals.first{it.id==r.goalId};Triple(g.content,g.priority,g.color)}}}
private fun editItem(ps:List<Project>,r:SelectedItem,n:String,pri:String,c:Long)=ps.map{p->if(p.id!=r.projectId)p else when(r.kind){ItemKind.PROJECT->p.copy(name=n,color=c);ItemKind.SUBPROJECT->p.copy(children=p.children.map{if(it.id==r.subId)it.copy(name=n)else it});ItemKind.GOAL->if(r.subId==null)p.copy(goals=p.goals.map{if(it.id==r.goalId)it.copy(content=n,priority=pri,color=c)else it})else p.copy(children=p.children.map{s->if(s.id==r.subId)s.copy(goals=s.goals.map{if(it.id==r.goalId)it.copy(content=n,priority=pri,color=c)else it})else s})}}
private fun deleteItem(ps:List<Project>,r:SelectedItem)=when(r.kind){ItemKind.PROJECT->ps.filterNot{it.id==r.projectId};ItemKind.SUBPROJECT->ps.map{if(it.id==r.projectId)it.copy(children=it.children.filterNot{s->s.id==r.subId})else it};ItemKind.GOAL->ps.map{p->if(p.id!=r.projectId)p else if(r.subId==null)p.copy(goals=p.goals.filterNot{it.id==r.goalId})else p.copy(children=p.children.map{s->if(s.id==r.subId)s.copy(goals=s.goals.filterNot{it.id==r.goalId})else s})}}

private class LocalStore(c:Context){private val p=c.getSharedPreferences("progress_nest",Context.MODE_PRIVATE);fun loadTheme()=runCatching{ThemeChoice.valueOf(p.getString("theme","SYSTEM")!!)}.getOrDefault(ThemeChoice.SYSTEM);fun saveTheme(v:ThemeChoice){p.edit().putString("theme",v.name).apply()};fun loadProjects():List<Project>{val raw=p.getString("projects",null)?:return sample();return runCatching{val a=JSONArray(raw);List(a.length()){a.getJSONObject(it).project()}}.getOrElse{sample()}};fun saveProjects(v:List<Project>){p.edit().putString("projects",JSONArray().apply{v.forEach{put(it.json())}}.toString()).apply()}}
private fun sample()=listOf(Project(1,"个人成长",0xFF6750A4,listOf(Goal(2,"完成本月复盘","中",0xFF3679C8)),listOf(SubProject(3,"阅读计划",listOf(Goal(4,"读完《深度工作》","高",0xFFE05D5D,true))))))
private fun Goal.json()=JSONObject().put("id",id).put("content",content).put("priority",priority).put("color",color).put("done",done)
private fun SubProject.json()=JSONObject().put("id",id).put("name",name).put("goals",JSONArray().apply{goals.forEach{put(it.json())}})
private fun Project.json()=JSONObject().put("id",id).put("name",name).put("color",color).put("goals",JSONArray().apply{goals.forEach{put(it.json())}}).put("children",JSONArray().apply{children.forEach{put(it.json())}})
private fun JSONObject.goal()=Goal(getLong("id"),getString("content"),getString("priority"),getLong("color"),optBoolean("done"))
private fun JSONObject.sub()=SubProject(getLong("id"),getString("name"),getJSONArray("goals").let{a->List(a.length()){a.getJSONObject(it).goal()}})
private fun JSONObject.project()=Project(getLong("id"),getString("name"),getLong("color"),getJSONArray("goals").let{a->List(a.length()){a.getJSONObject(it).goal()}},getJSONArray("children").let{a->List(a.length()){a.getJSONObject(it).sub()}})
