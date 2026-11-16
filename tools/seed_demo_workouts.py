"""Add labelled demo history to the primary emulator, preserving every existing record."""
from pathlib import Path
import subprocess,sqlite3,json,datetime as dt,shutil,argparse,os
from zoneinfo import ZoneInfo

parser=argparse.ArgumentParser(description="Add labelled demo history to an emulator. Requires Python 3.9+ and adb.")
parser.add_argument("--serial",required=True,help="Emulator serial from adb devices (emulator-xxxx)")
parser.add_argument("--adb",default="adb",help="Path to adb executable")
parser.add_argument("--output",required=True,type=Path,help="Local directory for database backup and summary; keep outside the repository")
args=parser.parse_args()
if not args.serial.startswith("emulator-"):parser.error("This helper only supports emulators, not physical phones")
ROOT=args.output.resolve();ROOT.mkdir(parents=True,exist_ok=True)
ADB=args.adb
SERIAL=args.serial
def adb(*args):
    return subprocess.run([ADB,'-s',SERIAL,*args],capture_output=True,check=True).stdout

epoch=int(adb('shell','date','+%s').strip())
zone=adb('shell','getprop','persist.sys.timezone').decode().strip()
try:
    device_zone=dt.timezone.utc if zone in ('GMT','UTC','Etc/UTC') else ZoneInfo(zone)
except Exception as error:
    raise SystemExit('Timezone unavailable: install the Python tzdata package before continuing') from error
now=dt.datetime.fromtimestamp(epoch,device_zone)
monday=(now-dt.timedelta(days=now.weekday())).replace(hour=0,minute=0,second=0,microsecond=0)
stamp=dt.datetime.now().strftime('%Y%m%d-%H%M%S')
backup=ROOT/'FitPlan-data-backups'/stamp
backup.mkdir(parents=True)
adb('shell','am','force-stop','com.example.fitplan')
names=adb('shell','run-as','com.example.fitplan','ls','databases').decode().split()
for name in names:
    if name.startswith('fitplan.db'):
        (backup/name).write_bytes(adb('exec-out','run-as','com.example.fitplan','cat','databases/'+name))
working=backup/'working'
working.mkdir()
for file in backup.iterdir():
    if file.is_file():shutil.copy2(file,working/file.name)
dbpath=working/'fitplan.db'
exercises={
    0:('杠铃卧推','chest',40),1:('哑铃上斜卧推','chest',16),
    3:('哑铃推举','shoulders',12),4:('哑铃侧平举','shoulders',5),
    6:('高位下拉','back',35),7:('坐姿划船','back',30),
    9:('深蹲','legs',50),10:('腿举','legs',70),11:('腿弯举','legs',20),
    12:('哑铃弯举','arms',8),13:('绳索下压','arms',15),14:('卷腹','core',0),
}
templates=[('胸肩训练',[0,1,3,4]),('背部与手臂',[6,7,12,13]),('腿部与核心',[9,10,11,14]),('上肢综合',[0,6,4,12]),('轻量恢复',[1,7,14])]
# Includes a lighter week and a plateau; demo progress is intentionally not monotonic.
counts=[3,3,4,3,4,5,4,1]
load_steps=[0,1,2,2,3,4,5,5]
summary=[]; inserted=[]
with sqlite3.connect(dbpath) as db:
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    original={row[0]:row[1:] for row in db.execute('SELECT id,kind,body,updated FROM documents')}
    for week in range(8):
        start=monday-dt.timedelta(weeks=7-week)
        count=counts[week]
        if week==7:count=min(3,now.weekday()+1)
        offsets={1:[0],2:[0,2],3:[0,2,5],4:[0,1,3,5],5:[0,1,3,4,6]}[count]
        if week==7:offsets=list(range(count))
        weekly_sets=0;weekly_volume=0;actual=0
        for index,offset in enumerate(offsets):
            end=start+dt.timedelta(days=offset,hours=18,minutes=15)
            if end>=now:end=now-dt.timedelta(minutes=10)
            if end<start:continue
            title,choices=templates[index]
            duration=32+week*2+index*4
            began=max(start,end-dt.timedelta(minutes=duration))
            rows=[]
            for order,eid in enumerate(choices):
                name,part,base=exercises[eid]
                step=2.5 if base>=30 else 1
                weight=base+load_steps[week]*step if base else 0
                sets=2 if week==3 else (4 if week>=4 and order<2 else 3)
                reps=12 if base==0 or eid in (4,12,13) else 10
                logs=[{'weight':weight,'reps':reps-(1 if s==sets-1 and week==3 else 0),'done':True} for s in range(sets)]
                if week==3 and order==0:logs.append({'weight':weight,'reps':reps,'done':False})
                weekly_sets+=sets
                weekly_volume+=sum(log['weight']*log['reps'] for log in logs if log['done'])
                rows.append({'exerciseId':f'builtin-{eid}','name':name,'part':part,'weight':weight,'sets':len(logs),'reps':reps,'logs':logs})
            identity=f'demo-eightweeks-v1-{start.date()}-{index}'
            if identity in original:
                assert json.loads(original[identity][1]).get('demoDataset')=='eightweeks-v1'
            checkin={'day':began.date().isoformat(),'parts':list(dict.fromkeys(row['part'] for row in rows)),'minutes':duration,'volume':1,'sleep':2 if week==3 else 0,'food':0,'soreness':1 if week==3 else 0,'notes':'测试数据：用于演示周报，不是真实训练记录。'}
            record={'id':identity,'name':f'[测试数据] {title}','started':int(began.timestamp()*1000),'finished':int(end.timestamp()*1000),'created':int(began.timestamp()*1000),'checkin':checkin,'rows':rows,'demoDataset':'eightweeks-v1'}
            db.execute('INSERT OR REPLACE INTO documents (id,kind,body,updated) VALUES (?,?,?,?)',(identity,'history',json.dumps(record,ensure_ascii=False),record['finished']))
            inserted.append(identity);actual+=1
        summary.append({'week':str(start.date()),'workouts':actual,'completedSets':weekly_sets,'volumeKgReps':weekly_volume,'partial':week==7})
    for identity,fields in original.items():
        if identity not in inserted:assert db.execute('SELECT kind,body,updated FROM documents WHERE id=?',(identity,)).fetchone()==fields
    db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
adb('push',str(dbpath),'/data/local/tmp/fitplan-demo-merged.db')
adb('shell','run-as','com.example.fitplan','cp','/data/local/tmp/fitplan-demo-merged.db','databases/fitplan-demo-merged.db')
adb('shell','run-as','com.example.fitplan','mv','databases/fitplan-demo-merged.db','databases/fitplan.db')
for name in names:
    if name in ('fitplan.db-journal','fitplan.db-wal','fitplan.db-shm'):
        adb('shell','run-as','com.example.fitplan','rm','-f','databases/'+name)
verified=backup/'verified.db'
verified.write_bytes(adb('exec-out','run-as','com.example.fitplan','cat','databases/fitplan.db'))
with sqlite3.connect(verified) as db:
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    for identity in inserted:assert db.execute('SELECT kind FROM documents WHERE id=?',(identity,)).fetchone()==('history',)
result={'added_workouts':len(inserted),'existing_records_preserved':len([i for i in original if i not in inserted]),'device_timezone':zone,'backup':str(backup),'weeks':summary,'ids':inserted}
(ROOT/'FitPlan-demo-data-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='ids'},ensure_ascii=False,indent=2),flush=True)
print(adb('shell','am','start','-W','-n','com.example.fitplan/.MainActivity').decode(),flush=True)
