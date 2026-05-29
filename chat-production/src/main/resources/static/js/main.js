// Frontend SPA for RealtimeChat (vanilla JS)
(function(){
  const API = '';
  const state = { token: null, refreshToken: null, user: null, stompClient: null, currentConversationId: null, subscriptions: {} };

  // Helpers
  function q(id){return document.getElementById(id)}
  function jsonBody(o){return JSON.stringify(o)}

  async function apiFetch(path, opts={}){
    opts.headers = opts.headers || {};
    if (!opts.headers['Content-Type'] && !(opts.body instanceof FormData)) opts.headers['Content-Type']='application/json';
    const token = localStorage.getItem('accessToken');
    if (token) opts.headers['Authorization']='Bearer '+token;
    let res = await fetch(API + path, opts);
    if (res.status===401){
      const ok = await tryRefresh();
      if (ok){
        const token2 = localStorage.getItem('accessToken');
        opts.headers['Authorization']='Bearer '+token2;
        res = await fetch(API + path, opts);
      }
    }
    return res;
  }

  async function tryRefresh(){
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return false;
    try{
      const r = await fetch(API + '/api/auth/refresh', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken})});
      if (!r.ok) { logout(); return false; }
      const data = await r.json();
      localStorage.setItem('accessToken', data.accessToken);
      return true;
    }catch(e){console.warn('refresh failed',e);logout();return false}
  }

  // Auth
  async function login(username,password){
    const r = await fetch(API + '/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})});
    if (!r.ok){ alert('Login failed'); return false }
    const data = await r.json();
    localStorage.setItem('accessToken', data.accessToken);
    if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
    await loadMe();
    connectWS();
    renderAuthState();
    loadConversations();
    loadNotifications();
    return true;
  }

  async function register(username,email,password){
    const r = await fetch(API + '/api/users/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,email,password})});
    if (!r.ok){ alert('Register failed'); return false }
    alert('Registered. You can login now.');
    return true;
  }

  function logout(){
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    state.user = null;
    if (state.stompClient) try{state.stompClient.disconnect()}catch(e){}
    state.stompClient=null;
    renderAuthState();
  }

  async function loadMe(){
    const r = await apiFetch('/api/users/me');
    if (!r.ok) return; state.user = await r.json(); renderProfile();
  }

  // Conversations
  async function loadConversations(){
    const r = await apiFetch('/api/conversations');
    if (!r.ok){ console.warn('Load convs failed'); return }
    const list = await r.json(); renderConversations(list);
  }

  function renderConversations(list){
    const out = q('conversations'); out.innerHTML='';
    list.forEach(c=>{
      const d = document.createElement('div'); d.className='conv'; d.textContent = c.conversationName || ('Conversation '+c.id);
      d.onclick = ()=>openConversation(c.id, c.conversationName);
      out.appendChild(d);
    });
  }

  async function openConversation(id,name){
    state.currentConversationId = id; q('chat-header').textContent = name || id; subscribeConversation(id); loadMessages(id);
  }

  async function loadMessages(conversationId){
    const r = await apiFetch('/api/conversations/'+conversationId+'/messages?page=1&pageSize=100');
    if (!r.ok){ console.warn('load messages failed'); return }
    const msgs = await r.json(); renderMessages(msgs);
  }

  function renderMessages(msgs){
    const out = q('messages'); out.innerHTML='';
    msgs.forEach(m=>appendMessage(m));
    out.scrollTop = out.scrollHeight;
  }

  function appendMessage(m){
    const out = q('messages');
    const wrapper = document.createElement('div'); wrapper.className = 'msg' + ((state.user && state.user.id===m.senderId)?' me':'');
    const b = document.createElement('div'); b.className='bubble' + ((state.user && state.user.id===m.senderId)?' me':'');
    b.textContent = m.content || ('['+ (m.messageType||'') +']');
    wrapper.appendChild(b); out.appendChild(wrapper); out.scrollTop = out.scrollHeight;
  }

  // Send message via WebSocket if available, else REST
  function sendMessage(){
    const text = q('messageInput').value.trim(); const file = q('fileInput').files[0];
    if(!text && !file) return;
    if (file){
      // create a temporary message via REST then upload
      const payload = { conversationId: state.currentConversationId, senderId: state.user ? state.user.id : null, content: 'file', messageType: 'file' };
      apiFetch('/api/messages',{method:'POST',body:jsonBody(payload)}).then(async r=>{
        if (!r.ok) { alert('send failed'); return }
        const sent = await r.json();
        const form = new FormData(); form.append('file', file); form.append('messageId', sent.id);
        await apiFetch('/api/files/upload',{method:'POST',body:form});
        q('fileInput').value='';
      });
      return;
    }
    const msg = { conversationId: state.currentConversationId, senderId: state.user ? state.user.id : null, content: text, messageType: 'text' };
    if (state.stompClient && state.stompClient.connected){
      try{
        state.stompClient.send('/app/chat/'+state.currentConversationId, {}, JSON.stringify(msg));
      }catch(e){ console.warn(e); apiFetch('/api/messages',{method:'POST',body:jsonBody(msg)}).then(()=>{}); }
    } else {
      apiFetch('/api/messages',{method:'POST',body:jsonBody(msg)}).then(()=>{});
    }
    q('messageInput').value='';
  }

  // WebSocket
  function connectWS(){
    if (state.stompClient && state.stompClient.connected) return;
    const socket = new SockJS('/ws');
    const client = Stomp.over(socket);
    client.debug = ()=>{};
    const headers = {};
    const token = localStorage.getItem('accessToken');
    if (token) headers.Authorization = 'Bearer ' + token;
    client.connect(headers, frame => {
      state.stompClient = client;
      console.log('STOMP connected');
      if (state.currentConversationId) subscribeConversation(state.currentConversationId);
    }, err => { console.warn('STOMP err', err); setTimeout(connectWS,3000); });
  }

  function subscribeConversation(id){
    if (!state.stompClient) return; if (state.subscriptions[id]) return;
    const sub = state.stompClient.subscribe('/topic/conversation/'+id, m=>{
      try{ const payload = JSON.parse(m.body); appendMessage(payload); }catch(e){ console.warn(e) }
    });
    state.subscriptions[id]=sub;
  }

  // Profile, friends, notifications
  function renderProfile(){
    const p = q('profileInfo'); if (!state.user) p.textContent='Not logged in'; else p.innerHTML = `<div><strong>${state.user.username}</strong></div><div>${state.user.email||''}</div>`;
  }

  async function loadNotifications(){
    const r = await apiFetch('/api/notifications'); if (!r.ok) return; const list = await r.json(); renderNotifications(list);
  }

  function renderNotifications(list){ const out = q('notificationsList'); out.innerHTML=''; list.forEach(n=>{ const d=document.createElement('div'); d.textContent = n.payload || n.type; out.appendChild(d); }); }

  // Simple friends search and request
  async function searchUsers(qs){
    if (!qs) return; const r = await apiFetch('/api/users/search?q='+encodeURIComponent(qs)); if (!r.ok) return; const list = await r.json(); renderSearchResults(list);
  }

  function renderSearchResults(list){ const out = q('friendsList'); out.innerHTML=''; list.forEach(u=>{ const d=document.createElement('div'); d.textContent = u.username + ' (' + (u.fullName||'') + ')'; const btn = document.createElement('button'); btn.textContent='Add'; btn.onclick=()=>sendFriendRequest(u.id); d.appendChild(btn); out.appendChild(d); }); }

  async function sendFriendRequest(userId){ await apiFetch('/api/friends/request',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({responderId:userId})}); alert('Request sent'); }

  // UI wiring
  function showModal(title, bodyHtml, submitCb){ q('modalTitle').textContent=title; q('modalBody').innerHTML=bodyHtml; q('modal').style.display='flex'; q('modalSubmit').onclick = ()=>{ submitCb(); q('modal').style.display='none'; }; q('modalClose').onclick = ()=>{ q('modal').style.display='none'; } }

  function renderAuthState(){ const token = localStorage.getItem('accessToken'); if (token){ q('btn-login').style.display='none'; q('btn-register').style.display='none'; q('btn-logout').style.display='inline-block'; } else { q('btn-login').style.display='inline-block'; q('btn-register').style.display='inline-block'; q('btn-logout').style.display='none'; } }

  // Events
  function attachEvents(){
    q('btn-login').onclick = ()=>{
      showModal('Login', `<div><label>Username</label><input id="m_user"/></div><div><label>Password</label><input id="m_pass" type="password"/></div>`, async ()=>{ const u=q('m_user').value, p=q('m_pass').value; await login(u,p); });
    };
    q('btn-register').onclick = ()=>{
      showModal('Register', `<div><label>Username</label><input id="r_user"/></div><div><label>Email</label><input id="r_email"/></div><div><label>Password</label><input id="r_pass" type="password"/></div>`, async ()=>{ const u=q('r_user').value, e=q('r_email').value, p=q('r_pass').value; await register(u,e,p); });
    };
    q('btn-logout').onclick = ()=>{ logout(); };
    q('btn-send').onclick = sendMessage;
    q('btn-new-conversation').onclick = ()=>{ showModal('New Conversation', `<div><label>Name</label><input id="conv_name"/></div>`, async ()=>{ const name=q('conv_name').value; await apiFetch('/api/conversations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({conversationName:name,isGroup:false})}); await loadConversations(); }); };
    q('searchUsers').oninput = (e)=>{ const v=e.target.value; if (v.length>2) searchUsers(v); }
  }

  // Init
  function init(){ renderAuthState(); attachEvents(); const token = localStorage.getItem('accessToken'); if (token){ loadMe().then(()=>{ connectWS(); loadConversations(); loadNotifications(); }); }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
