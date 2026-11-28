package com.provguard.web;

final class DashboardStyle {
    private DashboardStyle() {}

    static final String CSS = """
            :root{color-scheme:dark;--bg:#0b100e;--panel:#1b142b;--line:#3b2b54;--text:#eff5ed;--muted:#a7b7aa;--lime:#c0f775;--mint:#71dac1;--mono:Consolas,monospace;--radius:18px}
            *{box-sizing:border-box} html{scroll-behavior:smooth} body{margin:0;background:var(--bg);color:var(--text);font:16px/1.55 "Segoe UI",system-ui,sans-serif}
            a{color:inherit;text-decoration:none} a:focus-visible,button:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid var(--lime);outline-offset:4px}
            .shell{width:min(1190px,calc(100% - 64px));margin-inline:auto}.top{border-bottom:1px solid var(--line);background:#0d1410}.nav{height:72px;display:flex;align-items:center;justify-content:space-between;gap:20px}.nav nav{display:flex;gap:30px;color:var(--muted);font-size:.94rem}.nav nav a:hover,.api-links a:hover,.footer a:hover{color:var(--lime)}
            .brand{display:inline-flex;align-items:center;gap:11px;font-weight:760;font-size:1.25rem;letter-spacing:-.04em}.brand-icon{width:26px;height:26px;border:2px solid var(--lime);border-radius:8px;display:grid;place-items:center;transform:rotate(-8deg)}.brand-icon:after{content:"";width:9px;height:9px;border:2px solid var(--lime);border-radius:3px}
            .nav-action,.state{display:inline-flex;align-items:center;gap:8px;border:1px solid var(--line);padding:7px 12px;border-radius:999px;font-size:.85rem;color:var(--muted)}.dot{display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--mint);box-shadow:0 0 12px #71dac178}.state.demo{color:var(--lime)}
            .hero{display:grid;grid-template-columns:1.1fr .9fr;gap:42px;align-items:center;padding:68px 0 44px}.hero-copy{animation:arrive .65s ease-out both}.eyebrow{margin:0 0 13px;color:var(--lime);font:600 .78rem/1.4 var(--mono);letter-spacing:.10em}.eyebrow span{display:inline-block;width:19px;height:1px;margin:0 7px 3px 0;background:var(--lime)}
            h1,h2,h3,p{margin-top:0}h1{font-size:clamp(3.35rem,6.4vw,6rem);line-height:.98;letter-spacing:-.078em;margin:0 0 24px;font-weight:790}h1 em{color:var(--lime);font-style:normal}.intro{max-width:450px;color:var(--muted);font-size:1.14rem;margin-bottom:24px}
            .actions,.api-links{display:flex;gap:12px;flex-wrap:wrap}.button,button{display:inline-flex;align-items:center;justify-content:center;padding:10px 17px;border:1px solid var(--line);border-radius:9px;background:#2a1c3e;color:var(--text);font-family:inherit;font-size:.93rem;font-weight:650;line-height:1.4;cursor:pointer;transition:background .2s,border-color .2s,transform .2s}.button:hover,button:hover{transform:translateY(-2px);border-color:var(--lime)}.primary,button{background:var(--lime);color:#21112f;border-color:var(--lime);font-weight:720}
            .scope{position:relative;min-height:320px;overflow:hidden;display:grid;place-items:center;border:1px solid #345040;border-radius:var(--radius);background:radial-gradient(ellipse at center,#1d3725 0%,#181024 60%,#111a14 100%);animation:arrive .7s .12s ease-out both}.scope:before{content:"";position:absolute;inset:0;background-image:linear-gradient(#81bf7e18 1px,transparent 1px),linear-gradient(90deg,#81bf7e18 1px,transparent 1px);background-size:24px 24px;mask-image:radial-gradient(ellipse,#000 34%,transparent 85%)}
            .scope-top,.scope-bottom{position:absolute;left:18px;right:18px;display:flex;justify-content:space-between;gap:8px;color:#9ac6a5;font:.72rem var(--mono);letter-spacing:.08em}.scope-top{top:16px}.scope-bottom{bottom:16px}.scope-bottom b{color:var(--lime)}
            .radar{position:relative;width:230px;aspect-ratio:1;border-radius:50%;display:grid;place-items:center;border:1px solid #a9f58b57;overflow:hidden}.radar i{position:absolute;inset:16%;border:1px solid #a9f58b42;border-radius:50%}.radar i:nth-child(2){inset:31%}.radar i:nth-child(3){inset:45%;background:#c0f77534;box-shadow:0 0 32px #c0f77555}.radar b{position:absolute;inset:0;background:conic-gradient(from 0deg,transparent 72%,#a6ed803d 98%,#a6ed80cc 100%);animation:sweep 8s linear infinite}.radar strong{z-index:1;color:#e2ffd3;font:750 1.15rem var(--mono);letter-spacing:.12em;text-shadow:0 0 18px #b7f88b}.radar:before,.radar:after{content:"";position:absolute;background:#a9f58b32}.radar:before{width:100%;height:1px}.radar:after{height:100%;width:1px}
            .metrics,.pipeline{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.metrics article,.pipeline article,.evidence article,.event-detail{border:1px solid var(--line);background:var(--panel);border-radius:var(--radius);padding:20px 22px;transition:border-color .2s,transform .2s}.metrics article:hover,.pipeline article:hover,.evidence article:hover{border-color:#60875f;transform:translateY(-3px)}.metrics span{color:var(--muted);font-size:.9rem}.metrics strong{display:block;font-size:2.35rem;line-height:1.15;letter-spacing:-.06em;margin:5px 0}.metrics p,.pipeline p,.evidence p{color:var(--muted);margin-bottom:0}.metrics p{font-size:.85rem}
            .section{margin-top:66px;scroll-margin-top:18px}.section-head{display:flex;justify-content:space-between;align-items:end;gap:15px;margin-bottom:18px}.section-head .eyebrow{margin-bottom:7px}h2{font-size:clamp(2rem,4vw,3.15rem);letter-spacing:-.065em;line-height:1.05;margin-bottom:0}h3{font-size:1.18rem;letter-spacing:-.025em;margin-bottom:7px}
            .filters{display:flex;align-items:end;gap:14px;flex-wrap:wrap;margin-bottom:13px;padding:14px;border:1px solid var(--line);background:var(--panel);border-radius:12px}.filters label{display:grid;gap:4px;color:var(--muted);font-size:.88rem}.filters label:first-child{flex:1;min-width:210px}.filters input:not([type=checkbox]),select{width:100%;padding:9px 10px;background:#151021;border:1px solid var(--line);border-radius:7px;color:var(--text);font:inherit}.filters select{min-width:135px}.filters .toggle{display:flex;align-items:center;min-height:40px;gap:7px}input[type=checkbox]{accent-color:var(--lime)}
            .table-wrap{overflow:auto;border:1px solid var(--line);border-radius:13px;background:var(--panel)}table{width:100%;border-collapse:collapse;text-align:left;font-size:.86rem}caption{text-align:left;padding:12px 14px;color:var(--muted);font-size:.85rem}th,td{padding:10px 13px;border-top:1px solid var(--line)}th{color:var(--muted);white-space:nowrap;font-weight:600}td:first-child{white-space:nowrap;font-family:var(--mono);font-size:.78rem}td:last-child a,.event-detail a{color:var(--lime);overflow-wrap:anywhere}td:last-child a:hover,.event-detail a:hover{text-decoration:underline}tbody tr:hover{background:#2c203d}.outcome{display:inline-block;border-radius:7px;background:#203c31;color:#a4ebd4;padding:2px 7px;font-size:.78rem;white-space:nowrap}.outcome.flagged{background:#47391e;color:#ffdc9c}.outcome.blocked{background:#482929;color:#ffb7ae}.empty{color:var(--muted);white-space:normal!important;font:inherit!important;padding:23px 14px}iframe{display:block;width:100%;height:425px;border:1px solid var(--line);border-radius:13px;background:var(--panel)}.embedded table{border:0}.event-detail{margin-top:12px}.event-detail pre{white-space:pre-wrap;overflow-wrap:anywhere;color:var(--muted);font: .87rem/1.55 var(--mono)}
            .frames{padding-left:26px;color:var(--muted)}.frames li{padding:5px 0;overflow-wrap:anywhere}.frames small{margin-left:6px;color:var(--mint);font-family:var(--mono)}
            .api-links{margin-top:13px;gap:12px 22px;color:var(--muted);font-size:.9rem}.pipeline article>span{display:block;color:var(--lime);font:600 .76rem var(--mono);letter-spacing:.08em;margin-bottom:12px}.evidence{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px}.evidence article{background:linear-gradient(145deg,#302044,#1b142b 72%)}.evidence .eyebrow{margin-bottom:10px;color:var(--mint)}.evidence strong{display:block;font-size:2.6rem;letter-spacing:-.07em;color:var(--lime);margin-bottom:6px}
            .team .avatar{display:grid;place-items:center;width:42px;height:42px;border:1px solid #416348;background:#203124;border-radius:10px;font-size:.95rem}.footer{margin-top:64px;padding:21px 0;display:flex;justify-content:space-between;align-items:center;gap:12px;border-top:1px solid var(--line);font-size:.86rem;color:var(--muted)}.skip{position:absolute;top:-100px}.skip:focus{top:8px;left:8px;z-index:10;background:var(--lime);color:var(--bg);padding:8px}
            .hero{position:relative;padding-bottom:56px}.hero h1{max-width:690px}.hero:after{content:"";position:absolute;bottom:0;left:0;right:0;height:1px;background:linear-gradient(90deg,var(--line),#79b95b70,var(--line))}
            .section-intro{max-width:710px;color:var(--muted);margin:-3px 0 18px}.metrics{margin-bottom:14px}.benchmark{margin-top:14px}.limit-note{max-width:880px;color:var(--muted);margin:14px 0 0}
            .sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}
            .scenario{padding:0;margin:0;min-width:0;border:0}.scenario-tabs{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-bottom:9px}
            .scenario-tabs label{min-height:50px;display:flex;align-items:center;gap:9px;padding:9px 13px;border:1px solid #48365f;border-radius:10px;background:#1c132c;color:#bed0c0;font-weight:650;cursor:pointer;transition:background .2s,border-color .2s}.scenario-tabs label:hover{border-color:var(--lime)}.scenario-tabs label span{color:#8ca691;font:.83rem var(--mono)}
            #case-normal:checked~.scenario-tabs [for=case-normal],#case-drift:checked~.scenario-tabs [for=case-drift],#case-policy:checked~.scenario-tabs [for=case-policy]{background:var(--lime);border-color:var(--lime);color:#21112f}.scenario input:checked~.scenario-tabs label:hover span{color:inherit}
            #case-normal:checked~.scenario-tabs [for=case-normal] span,#case-drift:checked~.scenario-tabs [for=case-drift] span,#case-policy:checked~.scenario-tabs [for=case-policy] span{color:#533469}
            #case-normal:focus-visible~.scenario-tabs [for=case-normal],#case-drift:focus-visible~.scenario-tabs [for=case-drift],#case-policy:focus-visible~.scenario-tabs [for=case-policy]{outline:2px solid white;outline-offset:3px}
            .case-panels{border:1px solid #604980;border-radius:var(--radius);overflow:hidden;background:#20152f}.case-panel{display:none;grid-template-columns:1fr .86fr;min-height:325px}
            #case-normal:checked~.case-panels .normal-case,#case-drift:checked~.case-panels .drift-case,#case-policy:checked~.case-panels .policy-case{display:grid;animation:arrive .25s ease-out both}
            .trace{padding:24px;background:linear-gradient(145deg,#2c1c43,#181024);border-right:1px solid #48365f}.trace-step{display:grid;grid-template-columns:minmax(90px,.52fr) 1fr;gap:10px;align-items:center;position:relative;margin:0 0 10px;padding:8px 11px;border:1px solid #55406f;border-radius:7px;background:#1a1228}.trace-step:last-child{margin-bottom:0}.trace-step:not(:last-child):after{content:"";position:absolute;left:24px;top:100%;height:11px;width:1px;background:#a488d1}.trace-step span{font:600 .74rem var(--mono);color:#cfb7e8}.trace-step code{color:#f4e9ff;overflow-wrap:anywhere}.trace-step.changed{border-color:#906639;background:#30251b}.trace-step.changed span{color:#ffcd8f}.trace-step.sink{background:#392455;border-color:#9f78d2;color:var(--lime)}
            .verdict{padding:26px 26px 22px}.verdict .state{margin-bottom:14px}.verdict h3{font-size:clamp(1.3rem,2.5vw,1.9rem)}.verdict p:not(.state),.verdict>span{color:var(--muted);margin-bottom:8px}.verdict strong{display:block;color:var(--lime);font-size:clamp(2.4rem,5vw,4.15rem);line-height:1.05;letter-spacing:-.08em}.verdict>span{font-size:.92rem}.walk-footer{display:flex;justify-content:space-between;gap:10px;color:var(--muted);margin-top:12px;font-size:.88rem}.walk-footer a,.paper-note a{color:var(--lime)}
            .paper-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.paper-note,.transition{padding:22px;border:1px solid var(--line);border-radius:var(--radius);background:#1d142d}.paper-note .eyebrow{display:block;font-size:.76rem}.paper-note h3{font-size:1.4rem}.paper-note p,.transition p:last-child{color:var(--muted)}.paper-note.adapted{background:linear-gradient(130deg,#35234c,#1c132c 72%);border-color:#745292}.paper-note code{color:var(--lime)}.constraint{border-left:2px solid var(--mint);padding-left:10px}
            .transition{margin-top:14px}.transition-grid{display:grid;gap:7px;margin-bottom:14px}.transition-grid div{display:grid;grid-template-columns:145px 1fr;gap:12px;padding:8px 10px;border:1px solid #48365f;border-radius:7px;background:#151021}.transition-grid span{color:var(--muted)}.transition-grid code{overflow-wrap:anywhere}.transition-grid code b{color:var(--lime)}.transition p:last-child{margin-bottom:0}
            .engineering-list{display:grid;gap:8px}.engineering-list details{padding:13px 16px;border:1px solid var(--line);border-radius:10px;background:#1b142b}.engineering-list details[open]{border-color:#745292}.engineering-list summary{cursor:pointer;font-weight:700}.engineering-list summary span{margin-left:9px;color:var(--mint);font-weight:500;font-size:.86rem}.engineering-list details p{color:var(--muted);margin:7px 0 0}
            @keyframes sweep{to{transform:rotate(360deg)}}@keyframes arrive{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:none}}
            @media(max-width:850px){.hero{grid-template-columns:1fr;gap:24px;padding:45px 0 28px}.scope{min-height:265px}.radar{width:190px}.nav-action{display:none}.shell{width:calc(100% - 36px)}}
            @media(max-width:560px){.nav{height:62px}.nav nav{gap:12px;font-size:.85rem}.brand{font-size:1.06rem}.brand-icon{width:20px;height:20px}.hero h1{font-size:clamp(2.8rem,12vw,4.05rem)}.metrics,.pipeline,.evidence{grid-template-columns:1fr}.metrics{gap:8px}.metrics article{padding:11px 13px}.metrics strong{font-size:1.85rem}.section{margin-top:45px}.footer{align-items:start;flex-direction:column}.section-head{align-items:start;flex-direction:column}.scope{min-height:235px}}
            @media(max-width:700px){.case-panel,.paper-grid{grid-template-columns:1fr}.trace{border-right:0;border-bottom:1px solid #48365f}.walk-footer{flex-direction:column}.scenario-tabs label{align-items:start;flex-direction:column;gap:4px;font-size:.86rem}.transition-grid div{grid-template-columns:1fr;gap:3px}.nav nav a:nth-child(2){display:none}}
            body{font-family:Inter,"Segoe UI",system-ui,sans-serif;line-height:1.65}
            .nav nav{gap:5px}.nav nav a{padding:7px 12px;border-radius:8px;white-space:nowrap}.nav nav a[aria-current=page]{background:#233426;color:var(--lime)}
            .inner{min-height:62vh}.inner .section{margin-top:48px}.inner h1{font-size:clamp(2.45rem,5vw,4.25rem);line-height:1.06;letter-spacing:-.065em;margin:0}.inner .section-head{margin-bottom:24px}.section-intro{max-width:69ch;font-size:1.06rem}.route-cards a,.back{display:inline-block;color:var(--lime);margin-top:12px}.route-cards a:hover,.back:hover{text-decoration:underline}.back{margin:0 0 20px}.home-project{margin-top:40px}
            @media(max-width:760px){.nav{height:auto;flex-direction:column;align-items:stretch;gap:8px;padding:12px 0}.nav nav{overflow-x:auto;padding-bottom:5px;scrollbar-width:thin}.nav nav a:nth-child(2){display:block}.nav nav a{padding:6px 9px}.inner .section{margin-top:32px}}
            .workspace {
                --bg: #100b1d; --panel: #1b142b; --line: #3b2b54; --text: #f8f3ff;
                --muted: #c9b9da; --lime: #e7b9ff; --mint: #afabff; --radius: 13px;
                display: grid; grid-template-columns: 216px minmax(0, 1fr); min-height: 100vh;
            }
            .workspace-side {
                position: sticky; top: 0; height: 100dvh; display: flex; flex-direction: column;
                gap: 30px; padding: 24px 15px 16px; border-right: 1px solid var(--line); background: #151021;
            }
            .workspace-side .brand { margin: 0 9px; }
            .workspace-side nav { display: grid; gap: 5px; }
            .workspace-side nav a { padding: 9px 12px; border: 1px solid transparent; border-radius: 8px; color: var(--muted); font-size: .94rem; }
            .workspace-side nav a:hover { background: #251736; color: var(--text); }
            .workspace-side nav a[aria-current=page] { color: var(--lime); border-color: #9464c2; background: #35234c; }
            .workspace-source { margin-top: auto; padding: 10px 12px; border: 1px solid var(--line); border-radius: 9px; font-size: .82rem; color: var(--muted); }
            .workspace-main { min-width: 0; }
            .workspace-main .shell { width: min(1450px, calc(100% - 54px)); }
            .workspace-top {
                padding: 10px 27px; min-height: 63px; border-bottom: 1px solid var(--line);
                display: flex; align-items: center; justify-content: space-between; gap: 15px; color: var(--muted); font-size: .9rem;
            }
            .workspace-top strong { color: var(--text); }
            .workspace-top form { display: flex; gap: 7px; width: min(100%, 320px); }
            .workspace-top input { min-width: 0; width: 100%; padding: 7px 11px; border: 1px solid var(--line);
                border-radius: 8px; background: #1b1429; color: var(--text); font: inherit; }
            .workspace-top button { background: #291b3c; color: var(--text); border-color: var(--line); padding: 7px 10px; }
            .overview-hero {
                display: grid; grid-template-columns: 1.35fr minmax(0, 1fr); gap: 24px; align-items: center;
                margin: 24px 0 15px; padding: clamp(20px, 4vw, 38px); border-radius: var(--radius);
                border: 1px solid #794ca8; background: linear-gradient(125deg, #1d1230, #342052 58%, #211432);
            }
            .overview-hero h1 { font-size: clamp(2.35rem, 4.8vw, 4.75rem); letter-spacing: -.07em; margin-bottom: 13px; }
            .overview-hero p:not(.eyebrow), .latest-event code { color: var(--muted); }
            .latest-event { background: #1b102a; padding: 17px; border: 1px solid #624884; border-radius: 10px; }
            .latest-event h2, .panel-head h2, .inspector h2 { font-size: 1.12rem; letter-spacing: -.026em; line-height: 1.3; }
            .latest-event .latest-api { color: var(--text); margin: 12px 0 5px; overflow-wrap: anywhere; }
            .latest-event code { display: block; overflow-wrap: anywhere; font-size: .86rem; }
            .latest-event a, .panel-head a, .inspect-meta a { display: inline-block; color: var(--lime); margin-top: 10px; font-size: .89rem; }
            .latest-event a:hover, .panel-head a:hover, .inspect-meta a:hover { text-decoration: underline; }
            .overview-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
            .workspace .metrics article { padding: 13px 17px; }
            .workspace .metrics strong { font-size: 1.9rem; }
            .overview-grid { display: grid; grid-template-columns: minmax(0, 2.35fr) minmax(216px, 1fr); gap: 14px; }
            .workspace-panel { min-width: 0; background: var(--panel); border: 1px solid var(--line); border-radius: var(--radius); }
            .panel-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 13px 15px; border-bottom: 1px solid var(--line); }
            .panel-head a { margin: 0; white-space: nowrap; }
            .workspace-panel > .table-wrap { border: 0; border-radius: 0 0 var(--radius) var(--radius); }
            .outcome-meter { padding: 11px 15px 1px; }
            .outcome-meter div { display: flex; justify-content: space-between; gap: 10px; font-size: .9rem; }
            .outcome-meter meter { display: block; width: 100%; height: 12px; accent-color: var(--mint); }
            .outcome-meter.flagged meter::-webkit-meter-optimum-value { background: #d6b771; }
            .outcome-meter.blocked meter::-webkit-meter-optimum-value { background: #e28787; }
            .overview-links { margin-top: 14px; }
            .workflow-links { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
            .workflow-links a { display: grid; padding: 13px 15px; }
            .workflow-links a + a { border-left: 1px solid var(--line); }
            .workflow-links a:hover { background: #302143; }
            .workflow-links span { color: var(--muted); font-size: .88rem; }
            .capture-grid { display: grid; gap: 12px; }
            .capture-step, .inspect-meta { padding: 17px; }
            .capture-step pre { background: #130d20; padding: 12px; white-space: pre-wrap; overflow-wrap: anywhere; border-radius: 8px; }
            .inspector { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(235px, 1fr); margin-top: 18px; gap: 14px; }
            .inspector .event-detail { margin: 0; }
            .inspector-api { color: var(--muted); margin: 4px 0 12px; overflow-wrap: anywhere; }
            .inspect-meta dt { color: var(--muted); font-size: .83rem; }
            .inspect-meta dd { margin: 1px 0 10px; overflow-wrap: anywhere; }
            .workspace .inner .section { margin-top: 35px; }
            .workspace .inner h1 { font-size: clamp(2.3rem, 4vw, 3.5rem); }
            .workspace .overview-hero {
                background: radial-gradient(ellipse at 82% 12%, #7640a64d, transparent 62%), linear-gradient(120deg, #211434, #2c1c47);
                box-shadow: inset 0 1px #e7b9ff24, 0 12px 38px #4c23764d;
            }
            .workspace .primary, .workspace .filters button {
                background: linear-gradient(115deg, #f2c8ff, #caa8ff);
                border-color: #eed1ff;
                color: #251235;
                box-shadow: 0 3px 17px #c186fc4d;
            }
            .workspace .brand-icon { box-shadow: 0 0 16px #d99aff55; }
            .workspace-side nav a[aria-current=page] { box-shadow: inset 2px 0 #e7b9ff; }
            .execution-flow { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
            .execution-flow article { padding: 15px 17px; border: 1px solid var(--line); border-radius: 11px; background: var(--panel); }
            .execution-flow p, .capture-note { color: var(--muted); margin: 0; }
            .capture-note { margin: 13px 0 19px; max-width: 76ch; }
            .capture-commands { font-size: 1.55rem; letter-spacing: -.03em; margin: 25px 0 10px; }
            .execution-evidence { box-shadow: 0 5px 28px #65349b30; }
            .viva-hero {
                display: grid; grid-template-columns: 1.3fr 1fr; align-items: center; gap: clamp(20px, 4vw, 62px);
                margin-top: 26px; padding: clamp(24px, 4vw, 60px); border: 1px solid #a970ec;
                border-radius: 24px; background: radial-gradient(ellipse at 89% 25%, #8536df 0%, transparent 59%),
                    linear-gradient(125deg, #29134d 12%, #502690 100%);
                box-shadow: 0 15px 65px #9c55ff35;
            }
            .viva-hero h1 { margin: 10px 0 16px; font-size: clamp(2.55rem, 5vw, 5.2rem); line-height: 1.01; letter-spacing: -.067em; }
            .viva-hero h1 span { color: #eacaff; }
            .viva-hero .eyebrow { color: #dcf9a7; }
            .viva-hero p:not(.eyebrow) { color: #f1e1ff; font-size: 1.04rem; max-width: 44ch; }
            .viva-hero .button { margin-top: 12px; }
            .viva-diagram { display: grid; gap: 12px; padding: 0 0 0 16px; border-left: 2px solid #dab0ff; }
            .viva-diagram div {
                display: grid; gap: 3px; padding: 13px 15px; color: #fbf2ff;
                border-radius: 12px; border: 1px solid #cd9dfa; background: #301652c9;
                box-shadow: 0 6px 22px #1c0a3859; animation: viva-enter .46s ease-out both;
            }
            .viva-diagram div:nth-child(2) { animation-delay: .09s; }
            .viva-diagram div:nth-child(3) { animation-delay: .18s; }
            .viva-diagram b { color: #dcf9a7; }
            .viva-diagram span { font-size: .91rem; }
            .viva-frame { height: 398px; background: #251735; }
            .viva-panel { padding: 16px; background: #251735; }
            .viva-control { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
            .viva-control strong { font-size: 1.22rem; }
            .viva-control p { color: #e3d1f8; margin: 2px 0 0; }
            .viva-run { background: #ddfa9d; border-color: #ddfa9d; color: #1c2c08; box-shadow: 0 3px 18px #d7fa8f35; }
            .viva-progress { list-style: none; margin: 15px 0; padding: 0; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
            .viva-progress li { display: grid; gap: 2px; padding: 9px 12px; border: 1px solid #6c5287; border-radius: 10px; }
            .viva-progress span { font: .83rem/1.3 var(--mono); color: #e3d1f8; }
            .viva-progress li[data-state=Running] { border-color: #ffde8a; background: #684a493d; animation: viva-pulse 1.15s ease-in-out infinite; }
            .viva-progress li[data-state=Running] span { color: #ffde8a; }
            .viva-progress li[data-state=Complete] { border-color: #8eaa5f; }
            .viva-progress li[data-state=Complete] span { color: #ddfa9d; }
            .viva-empty { margin: 0; color: #e3d1f8; padding: 25px 16px; border: 1px dashed #765397; border-radius: 12px; }
            .viva-evidence { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
            .viva-card { --tone: #e5bcff; padding: 12px; display: flex; flex-direction: column; gap: 5px; min-width: 0;
                border: 1px solid #765397; border-top: 3px solid var(--tone); border-radius: 12px; background: #37214d; animation: viva-enter .32s ease-out both; }
            .viva-card.block { --tone: #ffcba1; }
            .viva-card.audit { --tone: #ddfa9d; }
            .viva-card h3 { color: var(--tone); font-size: clamp(1.35rem, 3vw, 2rem); line-height: 1.07; margin: 2px 0; letter-spacing: -.04em; }
            .viva-card p { margin: 0; color: #f0e1ff; }
            .viva-card p:first-child { font-weight: 650; }
            .viva-card p:nth-of-type(2) { flex: 1; }
            .viva-card a { align-self: start; margin-top: 6px; padding: 7px 10px; }
            .viva-terminal { padding: 15px 18px; margin-block: 18px; }
            .viva-terminal summary { cursor: pointer; font-weight: 650; }
            .viva-terminal pre { white-space: pre-wrap; overflow-wrap: anywhere; }
            .viva-terminal dd { margin: 5px 0 12px; overflow-wrap: anywhere; }
            .viva-links { margin-top: 16px; }
            @keyframes viva-enter { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
            @keyframes viva-pulse { 50% { box-shadow: inset 0 0 20px #ffde8a40; } }
            @media(max-width:1100px) { .overview-grid { grid-template-columns: 1fr; } }
            @media(max-width:850px) {
                .workspace { grid-template-columns: minmax(0, 1fr); }
                .workspace-side { position: static; height: auto; gap: 10px; padding: 14px 16px 8px; border: 0; border-bottom: 1px solid var(--line); }
                .workspace-side nav { display: flex; overflow-x: auto; scrollbar-width: thin; }
                .workspace-side nav a { white-space: nowrap; }
                .workspace-source { display: none; }
                .workspace-main .shell { width: calc(100% - 32px); }
                .workspace-top { padding-inline: 16px; }
            }
            @media(max-width:620px) {
                .overview-hero, .inspector, .execution-flow, .viva-evidence { grid-template-columns: minmax(0, 1fr); }
                .viva-hero { grid-template-columns: minmax(0, 1fr); }
                .viva-frame { height: 790px; }
                .viva-progress { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                .overview-hero { gap: 14px; }
                .overview-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                .workflow-links { grid-template-columns: 1fr; }
                .workflow-links a + a { border-left: 0; border-top: 1px solid var(--line); }
                .workspace-top { flex-wrap: wrap; }
                .workspace .inner .section { margin-top: 25px; }
            }
            .workspace, .viva-panel, .embedded, .theme-swatch {
                --chrome: #181025; --selection: #3b2658; --accent: #e5bcff; --accent-ink: #27133b;
                --signal: #dffa9e; --run-panel: #251735; --card-bg: #35224b;
                --hero-base: #271447; --hero-end: #50258f; --hero-glow: #8d3ff0; --hero-line: #af7af5;
            }
            :is(.workspace,.viva-panel,.embedded,.theme-swatch).theme-aurora {
                --bg: #071b21; --panel: #10292e; --line: #2b5660; --text: #eaffff; --muted: #b7d8dc;
                --lime: #9bf1ef; --mint: #aaf4b4; --chrome: #0b2228; --selection: #16434a;
                --accent: #95f0e6; --accent-ink: #09272a; --signal: #c5fdb6; --run-panel: #102b32; --card-bg: #173a43;
                --hero-base: #10313d; --hero-end: #125b62; --hero-glow: #117e8d; --hero-line: #62c4cb;
            }
            :is(.workspace,.viva-panel,.embedded,.theme-swatch).theme-projector {
                --bg: #090d19; --panel: #141e34; --line: #7a8ca8; --text: #fff; --muted: #e4ecff;
                --lime: #fff2ad; --mint: #bbdcff; --chrome: #111b2e; --selection: #263b5a;
                --accent: #fff0a1; --accent-ink: #1e1a07; --signal: #fff0a1; --run-panel: #17243b; --card-bg: #20314c;
                --hero-base: #142139; --hero-end: #243b63; --hero-glow: #375990; --hero-line: #95b4eb;
            }
            .workspace.theme-projector, .viva-panel.theme-projector, .embedded.theme-projector { font-size: 18px; }
            .theme-projector :is(.workspace-side nav a, .workspace-top, .viva-progress span, .viva-card a) { font-size: 1rem; }
            .workspace-side { background: var(--chrome); gap: 17px; }
            .workspace-side nav { overflow-y: auto; min-height: 0; }
            .workspace-side nav > span { margin: 8px 12px 1px; color: var(--muted); font-size: .78rem; font-weight: 650; }
            .workspace-side nav a { padding-block: 6px; }
            .workspace-side nav a[aria-current=page] { background: var(--selection); border-color: var(--hero-line); box-shadow: inset 2px 0 var(--accent); }
            .workspace-side nav a:hover, .workflow-links a:hover { background: var(--selection); }
            .workspace-top input, .workspace-top button { background: var(--chrome); }
            .top-actions { display: flex; justify-content: end; align-items: center; gap: 9px; min-width: 0; }
            .settings-link { padding: 6px 12px; border: 1px solid var(--line); border-radius: 8px; color: var(--text); }
            .settings-link:hover, .settings-link[aria-current=page] { background: var(--selection); border-color: var(--accent); }
            .workspace .primary, .workspace .filters button { background: var(--accent); color: var(--accent-ink); border-color: var(--accent); }
            .workspace.theme-aurora .overview-hero, .workspace.theme-projector .overview-hero {
                background: linear-gradient(115deg, var(--hero-base), var(--hero-end)); border-color: var(--hero-line);
            }
            .viva-hero { position: relative; isolation: isolate; overflow: hidden; border-color: var(--hero-line);
                background: radial-gradient(ellipse at 82% 34%, var(--hero-glow), transparent 65%),
                    linear-gradient(125deg, var(--hero-base) 12%, var(--hero-end));
                box-shadow: 0 12px 45px color-mix(in srgb, var(--hero-glow), transparent 71%); }
            .viva-hero::before { content: ""; position: absolute; inset: 0; z-index: -1; opacity: .65;
                background: repeating-linear-gradient(130deg, transparent 0 32px, #fff2 33px 34px, transparent 35px 56px);
                mask-image: linear-gradient(100deg, transparent 15%, #000);
                animation: runtime-field 9s linear infinite; }
            @keyframes runtime-field { to { background-position: 146px 0; } }
            .workspace .viva-hero h1 { font-size: clamp(2.55rem, 5vw, 5.1rem); }
            .viva-hero h1 span { color: var(--accent); }
            .viva-hero .eyebrow, .viva-diagram b { color: var(--signal); }
            .viva-diagram { border-color: var(--signal); }
            .viva-diagram div { position: relative; background: var(--hero-base); border-color: var(--hero-line); }
            .viva-diagram div::before { content: ""; position: absolute; top: 50%; left: -22px; width: 9px; height: 9px;
                border-radius: 50%; background: var(--signal); box-shadow: 0 0 11px var(--signal); }
            .viva-frame { height: 460px; background: var(--run-panel); }
            .viva-panel { background: var(--run-panel); }
            .viva-panel .viva-run { background: var(--signal); border-color: var(--signal); }
            .viva-card { --tone: var(--accent); background: var(--card-bg); }
            .viva-card.audit { --tone: var(--signal); }
            .viva-track { display: block; width: 100%; height: 8px; margin-top: 15px; border: 0; border-radius: 999px;
                background: var(--line); accent-color: var(--signal); overflow: hidden; }
            .viva-track::-webkit-progress-bar { background: var(--line); }
            .viva-track::-webkit-progress-value { background: var(--signal); }
            .run-links { display: flex; flex-wrap: wrap; gap: 8px 19px; margin-top: 14px; }
            .run-links a, .run-analysis .limit-note a { color: var(--accent); text-decoration: underline; text-underline-offset: 4px; }
            .history-list { display: grid; gap: 10px; margin-top: 20px; }
            .history-entry { display: grid; grid-template-columns: minmax(200px, 1.6fr) repeat(2, minmax(105px, .8fr)) auto;
                gap: 14px; align-items: center; padding: 15px 18px; }
            .history-entry h2 { font-size: 1.15rem; letter-spacing: -.02em; margin: 0 0 4px; }
            .history-entry time, .history-entry span { color: var(--muted); font-size: .91rem; }
            .history-entry span, .history-entry strong { display: block; }
            .history-entry strong { color: var(--accent); }
            .run-heading { display: flex; align-items: center; flex-wrap: wrap; justify-content: space-between; gap: 10px; margin: 12px 0 20px; }
            .run-heading p, .run-comparison p { color: var(--muted); margin: 0; }
            .run-comparison { padding: clamp(16px, 3vw, 26px); background: linear-gradient(150deg, var(--panel), var(--chrome)); }
            .run-comparison h2, .run-evidence-title { font-size: clamp(1.45rem, 3vw, 2.25rem); margin: 0 0 10px; }
            .run-evidence-title { margin-top: 24px; }
            .comparison-row { margin-top: 18px; }
            .comparison-row div { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 5px 16px; }
            .comparison-row strong { color: var(--accent); }
            .comparison-row meter { display: block; width: 100%; height: 17px; margin-top: 3px; }
            .comparison-row meter::-webkit-meter-bar { background: var(--line); border: 0; }
            .comparison-row meter::-webkit-meter-optimum-value { background: var(--accent); animation: viva-enter .4s ease-out; }
            .comparison-row.transitions meter::-webkit-meter-optimum-value { background: var(--signal); }
            .settings-page h1, .history-page h1 { margin-bottom: 13px; }
            .settings-page fieldset { border: 1px solid var(--line); border-radius: 12px; min-width: 0; padding: 14px; margin: 0 0 18px; }
            .settings-page legend { font-size: 1.12rem; font-weight: 650; padding: 0 7px; }
            .settings-themes { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
            .theme-option { display: grid; grid-template-columns: 1fr auto; align-items: start; gap: 4px 8px; padding: 11px;
                border: 1px solid var(--line); border-radius: 10px; background: var(--panel); cursor: pointer; }
            .theme-option:has(input:checked) { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
            .theme-option input { grid-column: 2; grid-row: 1; accent-color: var(--accent); }
            .theme-option strong { grid-column: 1; grid-row: 1; }
            .theme-option span { grid-column: 1 / -1; color: var(--muted); }
            .theme-swatch { display: flex; gap: 5px; align-items: end; height: 51px; border-radius: 7px; padding: 7px;
                background: linear-gradient(120deg, var(--hero-base), var(--hero-glow)); }
            .theme-swatch i { width: 20px; height: 13px; border-radius: 3px; background: var(--accent); }
            .theme-swatch i:nth-child(2) { width: 13px; height: 19px; background: var(--signal); }
            .theme-swatch i:nth-child(3) { width: 33px; height: 9px; opacity: .72; }
            .settings-motion label { display: block; color: var(--muted); margin-bottom: 4px; }
            .settings-motion select { max-width: 320px; background: var(--chrome); }
            .settings-page .secondary { background: var(--chrome); color: var(--text); }
            .saved-note { color: var(--signal); padding: 9px 12px; background: var(--panel); border-left: 3px solid var(--signal); }
            @media(max-width:850px) { .workspace-side nav > span { display: none; } }
            @media(max-width:680px) {
                .history-entry { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                .history-entry > :first-child, .history-entry .button { grid-column: 1 / -1; }
                .settings-themes { grid-template-columns: minmax(0, 1fr); }
            }
            @media(max-width:670px), (851px <= width <= 905px) { .viva-frame { height: 860px; } }
            @media(max-width:390px) { .viva-frame { height: 1030px; } }
            .reduce-motion *, .reduce-motion *::before, .reduce-motion *::after { animation: none !important; transition: none !important; }
            html:has(.reduce-motion) { scroll-behavior: auto; }
            @media(prefers-reduced-motion:reduce){*,*::before,*::after{animation:none!important;transition:none!important;scroll-behavior:auto!important}}
            """;
}
