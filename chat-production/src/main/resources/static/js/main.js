// Frontend SPA for RealtimeChat (vanilla JS)
(function(){
  const API = '';
  const state = { token: null, refreshToken: null, user: null, stompClient: null, currentConversationId: null, subscriptions: {}, friends: [] };

  // Helpers
  function q(id){return document.getElementById(id)}
  function jsonBody(o){return JSON.stringify(o)}

  // Theme helpers
  function getPreferredTheme(){
    return localStorage.getItem('theme') || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  }

  function applyTheme(t){
    try{
      if (!t || t==='light') { document.documentElement.removeAttribute('data-theme'); q('btn-theme').textContent='🌙'; }
      else { document.documentElement.setAttribute('data-theme','dark'); q('btn-theme').textContent='☀️'; }
      localStorage.setItem('theme', t);
    }catch(e){console.warn('applyTheme',e)}
  }

  function toggleTheme(){ applyTheme(getPreferredTheme()==='dark' ? 'light' : 'dark'); }

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
    await loadFriends();
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
      const d = document.createElement('div'); d.className='conv';
      const avatar = document.createElement('div'); avatar.className='conv-avatar';
      if (c.avatarUrl){ const img = document.createElement('img'); img.src = c.avatarUrl; img.alt=''; img.style.display='block'; img.style.width='100%'; img.style.height='100%'; img.style.objectFit='cover'; avatar.appendChild(img); }
      else { avatar.textContent = (c.conversationName||'').slice(0,1).toUpperCase() || shortId(c.id); }
      const info = document.createElement('div'); info.style.flex='1';
      const title = document.createElement('div'); title.textContent = c.conversationName || ('Conversation '+c.id); title.style.fontWeight='600';
      const sub = document.createElement('div'); sub.style.fontSize='12px'; sub.style.color='var(--muted)'; sub.textContent = c.lastMessagePreview || '';
      info.appendChild(title); info.appendChild(sub);
      d.appendChild(avatar); d.appendChild(info);
      d.onclick = ()=>openConversation(c.id, c.conversationName);
      out.appendChild(d);
    });
  }

  // Friends: load and render friend list, used for 1-1 chat and group creation
  async function loadFriends(){
    const outEl = q('friendsList'); if (!outEl) return;
    outEl.innerHTML = '<div style="color:var(--muted)">Loading friends...</div>';
    const r = await apiFetch('/api/friends');
    if (!r.ok){ outEl.innerHTML=''; return }
    const list = await r.json();
    const accepted = list.filter(f => f.status && String(f.status).toUpperCase() === 'ACCEPTED');
    const myId = state.user && state.user.id ? String(state.user.id) : null;
    const otherIds = accepted.map(f => (String(f.requesterId) === myId ? f.responderId : f.requesterId));
    const unique = Array.from(new Set(otherIds.map(String)));
    const users = await Promise.all(unique.map(id => apiFetch('/api/users/' + id).then(rr => rr.ok ? rr.json() : null).catch(()=>null)));
    const friends = users.filter(Boolean);
    state.friends = friends;
    renderFriends(friends);
    const btn = q('btn-new-conversation'); if (btn) btn.disabled = friends.length < 2; // require at least 2 friends to create a group (3 people total)
  }

  function renderFriends(list){
    const out = q('friendsList'); out.innerHTML='';
    if (!list || list.length===0){ out.innerHTML = '<div style="color:var(--muted)">(No friends yet)</div>'; return }
    list.forEach(u => {
      const d = document.createElement('div'); d.className='friend-item'; d.style.display='flex'; d.style.alignItems='center'; d.style.gap='8px'; d.style.padding='8px'; d.style.cursor='pointer';
      const av = document.createElement('div'); av.className='avatar'; av.style.width='36px'; av.style.height='36px'; av.style.fontSize='12px';
      if (u.avatarUrl){ const img = document.createElement('img'); img.src = u.avatarUrl; img.alt=''; img.style.width='100%'; img.style.height='100%'; img.style.borderRadius='50%'; av.appendChild(img); }
      else { av.textContent = (u.username||u.fullName||'').slice(0,2).toUpperCase(); }
      const info = document.createElement('div'); info.style.flex='1';
      const name = document.createElement('div'); name.textContent = u.username || u.fullName || u.email || 'Unknown'; name.style.fontWeight='600';
      const sub = document.createElement('div'); sub.textContent = u.email || ''; sub.style.fontSize='12px'; sub.style.color='var(--muted)';
      info.appendChild(name); info.appendChild(sub);
      d.appendChild(av); d.appendChild(info);
      // open direct chat on single click for better UX
      d.onclick = (e)=> { e.stopPropagation(); openDirectChat(u); };
      out.appendChild(d);
    });
  }

  // Start a direct 1-1 conversation with the given friend
  async function openDirectChat(friend){
    if (!state.user){ alert('Please login first'); return }
    try{
      // use server-side find-or-create for 1-1 conversations
      const payload = { conversationName: friend.username || friend.fullName || '', isGroup:false, memberIds: [String(friend.id)] };
      const cr = await apiFetch('/api/conversations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
      if (!cr.ok){ alert('Create or open conversation failed'); return }
      const conv = await cr.json();
      await loadConversations();
      await loadMe(); // refresh current user data to ensure profile avatar is current
      // Force the chat header to the friend's name for 1-1 conversations
      openConversation(conv.id, friend.username || friend.fullName || '');
    }catch(e){ console.warn(e); alert('Could not start chat with friend'); }
  }

  // Open group creation modal with friend checkboxes
  function openNewGroupModal(){
    if (!state.user){ alert('Please login first'); return; }
    if (!state.friends || state.friends.length===0){
      alert('Thật nực cười nếu tạo nhóm nhắn tin một mình! Hãy đi kết bạn trước nhé.');
      return;
    }
    const friendListHtml = state.friends.map(f => `<label style="display:block;margin:6px 0"><input type="checkbox" value="${f.id}"> ${f.username || f.fullName || f.email}</label>`).join('');
    const bodyHtml = `<div><label>Group name</label><input id="group_name" style="width:100%;padding:8px;border:1px solid #e5e7eb;border-radius:6px"/></div><div style="margin-top:8px"><strong>Select friends</strong></div><div id="group_friend_list" style="max-height:220px;overflow:auto;margin-top:6px">${friendListHtml}</div>`;
    showModal('Create Group', bodyHtml, async ()=>{
      const name = q('group_name').value || '';
      const checked = Array.from(document.querySelectorAll('#group_friend_list input[type=checkbox]:checked')).map(cb => cb.value);
      // require selecting at least 2 friends (group of 3 including creator)
      if (checked.length < 2){ alert('Vui lòng chọn ít nhất 2 bạn để tạo nhóm (tối thiểu 3 người).'); return; }
      const cr = await apiFetch('/api/conversations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({conversationName:name,isGroup:true, memberIds: checked})});
      if (!cr.ok){ alert('Create group failed'); return; }
      const conv = await cr.json();
      await loadConversations();
      openConversation(conv.id, name || 'Group');
    });
  }

  async function openConversation(id,name){
    state.currentConversationId = id;
    // If no name provided, or name equals current user's name, try to resolve a friendly name for 1-1 conversations
    if (!name || (state.user && (name === state.user.username || name === state.user.fullName))){
      try{
        const rr = await apiFetch('/api/conversations/' + id);
        if (rr.ok){
          const conv = await rr.json();
          if (conv){
            // prefer conv.conversationName unless it equals current user's name
            name = conv.conversationName;
            if (!name || name.trim()==='' || (state.user && (name === state.user.username || name === state.user.fullName))){
              if (!conv.isGroup){
                const mr = await apiFetch('/api/conversations/' + id + '/members');
                if (mr.ok){
                  const members = await mr.json();
                  if (Array.isArray(members)){
                    const other = members.find(m => String(m.id) !== String(state.user && state.user.id));
                    name = other ? (other.username || other.fullName || other.id) : ('Conversation '+id);
                  }
                }
              } else {
                name = 'Group';
              }
            }
          }
        }
      }catch(e){ console.warn('resolve conv name', e) }
    }
    q('chat-header').textContent = name || id;
    subscribeConversation(id); loadMessages(id);
  }

  async function loadMessages(conversationId){
    const r = await apiFetch('/api/conversations/'+conversationId+'/messages?page=1&pageSize=100');
    if (!r.ok){ console.warn('load messages failed'); return }
    let msgs = await r.json();
    if (!Array.isArray(msgs)) msgs = [];
    // sort messages oldest -> newest by sentAt (fallbacks handled)
    msgs.sort((a,b)=>{
      const ta = Date.parse(a && (a.sentAt || a.sentAtString || a.sentAtUtc || a.sentAtDate || a.createdAt) || 0) || 0;
      const tb = Date.parse(b && (b.sentAt || b.sentAtString || b.sentAtUtc || b.sentAtDate || b.createdAt) || 0) || 0;
      return ta - tb;
    });

    // attach sender user info by fetching users for unique senderIds
    state.userCache = state.userCache || {};
    const ids = Array.from(new Set(msgs.map(m => (m && (m.senderId || (m.sender && (m.sender.id || m.senderId)) ))).filter(Boolean)));
    const toFetch = ids.filter(id => !state.userCache[id]);
    await Promise.all(toFetch.map(async id => {
      try{
        const ur = await apiFetch('/api/users/' + id);
        if (ur.ok){ state.userCache[id] = await ur.json(); }
        else { state.userCache[id] = null; }
      }catch(e){ state.userCache[id] = null; }
    }));

    msgs.forEach(m => {
      try{ if (!m.sender && m.senderId && state.userCache[m.senderId]) m.sender = state.userCache[m.senderId]; }catch(e){}
    });

    renderMessages(msgs);
  }

  function renderMessages(msgs){
    const out = q('messages'); out.innerHTML='';
    msgs.forEach(m=>appendMessage(m));
    // keep scroll at bottom to show newest messages
    out.scrollTop = out.scrollHeight;
  }

  function formatTime(iso){
    if(!iso) return '';
    const d = new Date(iso);
    if (isNaN(d)) return '';
    return d.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});
  }

  function shortId(id){ if(!id) return '?'; return id.toString().slice(0,2).toUpperCase(); }
  function appendMessage(m){
    const out = q('messages');
    const wrapper = document.createElement('div');
    // normalize sender info
    const senderObj = (m && m.sender && typeof m.sender === 'object') ? m.sender : null;
    const senderId = m && (m.senderId || (senderObj && (senderObj.id || senderObj.userId)) || (typeof m.sender === 'string' ? m.sender : null));
    const isMe = state.user && String(state.user.id) === String(senderId);
    wrapper.className = 'msg' + (isMe? ' me':'');

    const avatarEl = document.createElement('div'); avatarEl.className='avatar';
    const avatarUrl = (m && (m.senderAvatarUrl || m.senderAvatar || (senderObj && (senderObj.avatarUrl || senderObj.avatar)) || m.avatarUrl));
    if (avatarUrl){ const img = document.createElement('img'); img.src = avatarUrl; img.alt='avatar'; img.style.display='block'; img.style.width='100%'; img.style.height='100%'; img.style.objectFit='cover'; avatarEl.appendChild(img); }
    else { avatarEl.textContent = shortId(m && (m.senderName || (senderObj && (senderObj.username || senderObj.fullName)) || senderId)); }

    const body = document.createElement('div'); body.className='msg-body';
    if (!isMe){ const name = document.createElement('div'); name.className='sender-name'; name.textContent = (m && (m.senderName || m.senderUsername || (senderObj && (senderObj.username || senderObj.fullName)) || senderId || 'Unknown')); body.appendChild(name); }

    const bubble = document.createElement('div'); bubble.className='bubble' + (isMe? ' me':'');
    // Render text content
    const text = (m && (m.content || ('['+ (m.messageType||'') +']')));
    if (text) {
      const p = document.createElement('div'); p.textContent = text; bubble.appendChild(p);
    }
    // Render attachments if any (added by backend)
    try{
      const atts = m && (m.attachments || m.attachmentsList || m.attachments || []);
      if (Array.isArray(atts) && atts.length>0){
        const attWrap = document.createElement('div'); attWrap.className='attachments';
        atts.forEach(a => {
          try{
            const t = (a && a.fileType) || (a && a.mimeType && a.mimeType.split('/')[0]) || 'file';
            const url = a.fileUrl || a.filePath || a.url;
            if (!url) return;
            if (t === 'image'){
              const img = document.createElement('img'); img.src = url; img.alt = a.fileName || 'image'; img.style.maxWidth='320px'; img.style.display='block'; img.style.marginTop='6px'; attWrap.appendChild(img);
            } else {
              const link = document.createElement('a'); link.href = url; link.textContent = a.fileName || url; link.target = '_blank'; link.rel='noopener noreferrer'; link.style.display='block'; link.style.marginTop='6px'; attWrap.appendChild(link);
            }
          }catch(e){ console.warn('render attachment', e); }
        });
        bubble.appendChild(attWrap);
      }
    }catch(e){ /* ignore */ }
    body.appendChild(bubble);

    const meta = document.createElement('div'); meta.className='meta'; meta.textContent = formatTime(m && (m.sentAt || m.sentAtString || m.sentAtUtc || m.sentAtDate) || new Date());
    body.appendChild(meta);

    if (isMe){ wrapper.appendChild(body); wrapper.appendChild(avatarEl); }
    else { wrapper.appendChild(avatarEl); wrapper.appendChild(body); }

    out.appendChild(wrapper); out.scrollTop = out.scrollHeight;
  }

  // Send message via WebSocket if available, else REST
  async function sendMessage(){
    const text = q('messageInput').value.trim(); const file = (q('fileInput') && q('fileInput').files) ? q('fileInput').files[0] : null;
    if(!text && !file) return;
    if (file){
      // create a temporary message via REST then upload
      const payload = { conversationId: state.currentConversationId, senderId: state.user ? state.user.id : null, content: 'file', messageType: 'file' };
      try{
        const r = await apiFetch('/api/messages',{method:'POST',body:jsonBody(payload)});
        if (!r.ok){ const txt = await r.text(); console.warn('create file message failed', txt); alert('Send failed'); return }
        const sent = await r.json();
        const form = new FormData(); form.append('file', file); form.append('messageId', sent.id);
        const up = await apiFetch('/api/files/upload',{method:'POST',body:form});
        if (!up.ok){ const txt = await up.text(); console.warn('file upload failed', txt); alert('File upload failed'); return }
        // reload messages for the conversation so attachment appears
        await loadMessages(state.currentConversationId);
        if (q('fileInput')) q('fileInput').value='';
      }catch(e){ console.warn('file send error', e); alert('Error sending file'); }
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
      // subscribe to personal notifications queue
      try{ client.subscribe('/user/queue/notifications', m=>{
          try{ const payload = JSON.parse(m.body); // payload is Notification object
            // prepend into notifications list
            const out = q('notificationsList'); if (out) {
              const itm = renderNotificationItem(payload); out.insertBefore(itm, out.firstChild);
            }
            // optionally show a small alert
            alert('You have a new notification');
          }catch(e){ console.warn('invalid notif', e) }
      }); }catch(e){console.warn('subscribe notif failed',e)}
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
    const p = q('profileInfo');
    p.innerHTML = '';
    if (!state.user){ p.textContent='Not logged in'; return; }

    const container = document.createElement('div');
    container.style.display = 'flex';
    container.style.alignItems = 'center';
    container.style.gap = '8px';

    const avatarDiv = document.createElement('div');
    avatarDiv.style.width = '48px'; avatarDiv.style.height = '48px'; avatarDiv.style.borderRadius = '50%'; avatarDiv.style.overflow = 'hidden';
    if (state.user.avatarUrl){
      const img = document.createElement('img'); img.src = state.user.avatarUrl; img.alt = ''; img.style.width='48px'; img.style.height='48px'; img.style.objectFit='cover';
      avatarDiv.appendChild(img);
    } else {
      const ph = document.createElement('div'); ph.style.width='48px'; ph.style.height='48px'; ph.style.display='flex'; ph.style.alignItems='center'; ph.style.justifyContent='center'; ph.style.background='var(--muted)'; ph.style.borderRadius='50%'; ph.textContent = (state.user.username||'U').slice(0,2).toUpperCase(); avatarDiv.appendChild(ph);
    }

    const info = document.createElement('div');
    const nameEl = document.createElement('div'); const strong = document.createElement('strong'); strong.textContent = state.user.username; nameEl.appendChild(strong);
    const emailEl = document.createElement('div'); emailEl.style.fontSize='13px'; emailEl.style.color='var(--muted)'; emailEl.textContent = state.user.email || '';
    info.appendChild(nameEl); info.appendChild(emailEl);

    container.appendChild(avatarDiv); container.appendChild(info);
    p.appendChild(container);

    const btn = document.createElement('button'); btn.textContent='Edit Profile'; btn.className='btn btn-outline'; btn.style.marginTop='8px'; btn.onclick = ()=> openEditProfileModal();
    p.appendChild(btn);
  }

  function openEditProfileModal(){
    if (!state.user) return; const current = state.user.avatarUrl || '';
    const body = `<div><label>Avatar URL</label><input id="edit_avatar_url" style="width:100%;padding:8px;border:1px solid #e5e7eb;border-radius:6px" value="${current}"/></div><div style="margin-top:8px"><label>Or choose file</label><input id="edit_avatar_file" type="file" accept="image/*"/></div>`;
    showModal('Edit Profile', body, async ()=>{
      try{
        const fileEl = q('edit_avatar_file');
        if (fileEl && fileEl.files && fileEl.files.length>0){
          const form = new FormData(); form.append('file', fileEl.files[0]);
          const r = await apiFetch('/api/users/me/avatar',{method:'POST',body:form});
          if (!r.ok){ alert('Upload failed'); return }
          await loadMe(); await loadFriends();
          alert('Cập nhật avatar thành công');
          return;
        }
        const v = q('edit_avatar_url').value || '';
        const r2 = await apiFetch('/api/users/me',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({avatarUrl:v})});
        if (!r2.ok){ alert('Update failed'); return }
        await loadMe(); await loadFriends();
        alert('Cập nhật hồ sơ thành công');
      }catch(e){ console.warn(e); alert('Update error'); }
    });
  }

  async function loadNotifications(){
    const r = await apiFetch('/api/notifications'); if (!r.ok) return; const list = await r.json(); renderNotifications(list);
  }

  function renderNotifications(list){
    const out = q('notificationsList'); out.innerHTML='';
    if (!list || list.length===0){ out.innerHTML = '<div style="color:var(--muted)">(No notifications)</div>'; return }
    list.forEach(n=>{ const item = renderNotificationItem(n); out.appendChild(item); });
  }

  function renderNotificationItem(n){
    const d = document.createElement('div'); d.style.padding='8px'; d.style.borderBottom='1px solid rgba(0,0,0,0.03)';
    if (n && n.id) d.dataset.notificationId = String(n.id);
    if (!n) return d;
    try{
      if (n.type === 'FRIEND_REQUEST'){
        const payload = typeof n.payload === 'string' ? JSON.parse(n.payload) : n.payload;
        const from = payload.from || payload.fromId || 'Someone';
        const friendshipId = payload.friendshipId;
        d.innerHTML = `<div><strong>${from}</strong> sent you a friend request</div>`;
        const actions = document.createElement('div'); actions.style.marginTop='6px';
        const btnAccept = document.createElement('button'); btnAccept.textContent='Accept'; btnAccept.className='btn'; btnAccept.style.marginRight='6px';
        btnAccept.onclick = async ()=>{ await respondFriendRequest(friendshipId, true, n.id); };
        const btnReject = document.createElement('button'); btnReject.textContent='Reject'; btnReject.className='btn btn-outline';
        btnReject.onclick = async ()=>{ await respondFriendRequest(friendshipId, false, n.id); };
        actions.appendChild(btnAccept); actions.appendChild(btnReject);
        d.appendChild(actions);
        return d;
      }
      if (n.type === 'MESSAGE'){
        const payload2 = typeof n.payload === 'string' ? JSON.parse(n.payload) : n.payload;
        const from = payload2.from || payload2.fromId || 'Someone';
        const convId = payload2.conversationId;
        const preview = payload2.preview || '';
        d.innerHTML = `<div style="cursor:pointer"><strong>${from}</strong> đã nhắn tin cho bạn: <div style="font-size:12px;color:var(--muted)">${preview}</div></div>`;
        d.onclick = async ()=>{
          try{
            // mark read
            if (n.id) await apiFetch('/api/notifications/'+n.id+'/read',{method:'POST'});
          }catch(e){}
          try{ if (convId) openConversation(convId, from); }catch(e){}
          try{ const el = document.querySelector(`[data-notification-id="${n.id}"]`); if (el) el.remove(); }catch(e){}
        };
        return d;
      }
      // default
      d.textContent = n.payload || n.type;
      return d;
    }catch(e){ d.textContent = n.payload || n.type; return d; }
  }

  async function respondFriendRequest(friendshipId, accept, notificationId){
    if (!friendshipId) { alert('Invalid request'); return }
    try{
      const url = `/api/friends/${friendshipId}/${accept? 'accept' : 'reject'}`;
      const r = await apiFetch(url,{method:'POST'});
      if (!r.ok){ alert('Action failed'); return }
      // mark notification read and remove it from DOM
      if (notificationId){
        const mr = await apiFetch('/api/notifications/'+notificationId+'/read',{method:'POST'});
        if (mr.ok){ try{ const el = document.querySelector(`[data-notification-id="${notificationId}"]`); if (el) el.remove(); }catch(e){} }
      }
      // refresh friends and notifications
      await loadFriends();
      await loadNotifications();
      // show success
      alert(accept ? 'Chấp nhận kết bạn thành công' : 'Từ chối yêu cầu thành công');
    }catch(e){ console.warn(e); alert('Error responding to friend request'); }
  }

  // Simple friends search and request
  async function searchUsers(qs){
    if (!qs) return; const r = await apiFetch('/api/users/search?q='+encodeURIComponent(qs)); if (!r.ok) return; const list = await r.json(); renderSearchResults(list);
  }

  function renderSearchResults(list){ const out = q('friendsList'); out.innerHTML=''; list.forEach(u=>{ const d=document.createElement('div'); d.textContent = u.username + ' (' + (u.fullName||'') + ')'; const btn = document.createElement('button'); btn.textContent='Add'; btn.onclick=()=>sendFriendRequest(u.id); d.appendChild(btn); out.appendChild(d); }); }

  function renderSearchResults(list){
    const out = q('searchResults') || q('friendsList'); out.innerHTML='';
    if (!list || list.length===0){ out.innerHTML = '<div style="color:var(--muted)">No users found</div>'; return }
    list.forEach(u=>{
      const d = document.createElement('div'); d.className='search-result-item'; d.style.display='flex'; d.style.alignItems='center'; d.style.justifyContent='space-between'; d.style.padding='6px';
      const left = document.createElement('div'); left.textContent = (u.username || u.fullName || u.email || 'Unknown');
      const right = document.createElement('div');
      const myId = state.user && state.user.id ? String(state.user.id) : null;
      const isMe = myId && String(u.id) === myId;
      const isFriend = state.friends && state.friends.find(f => String(f.id) === String(u.id));
      if (isMe){
        const span = document.createElement('span'); span.style.color='var(--muted)'; span.textContent='You'; right.appendChild(span);
      } else if (isFriend){
        const span = document.createElement('span'); span.style.color='var(--muted)'; span.textContent='Friend'; right.appendChild(span);
      } else {
        const btn = document.createElement('button'); btn.textContent='Add'; btn.className='btn';
        btn.onclick = async ()=>{ await sendFriendRequest(u.id, btn); };
        right.appendChild(btn);
      }
      d.appendChild(left); d.appendChild(right); out.appendChild(d);
    });
  }

  async function sendFriendRequest(userId, btnEl){
    try{
      const r = await apiFetch('/api/friends/request',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({responderId:userId})});
      if (!r.ok){ alert('Request failed'); return }
      if (btnEl){ btnEl.textContent='Requested'; btnEl.disabled = true; }
      alert('Request sent');
    }catch(e){ console.warn(e); alert('Request error'); }
  }

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
    if (q('btn-theme')) q('btn-theme').onclick = ()=>{ toggleTheme(); };
    if (q('btn-toggle-sidebar')) q('btn-toggle-sidebar').onclick = ()=>{
      const left = q('sidebar-left'), right = q('sidebar-right');
      if (!left || !right) return;
      const shown = window.getComputedStyle(left).display !== 'none';
      if (shown) { left.style.display='none'; right.style.display='none'; }
      else { left.style.display='block'; right.style.display='block'; }
    };
    q('btn-send').onclick = sendMessage;
    if (q('btn-new-conversation')){
      q('btn-new-conversation').textContent = 'New Group';
      q('btn-new-conversation').onclick = ()=>{ openNewGroupModal(); };
    }
    q('searchUsers').oninput = (e)=>{ const v=e.target.value; if (v.length>2) searchUsers(v); }
  }

  // Init
  function init(){
    renderAuthState();
    attachEvents();
    const token = localStorage.getItem('accessToken');
    if (token){
      loadMe().then(()=>{ connectWS(); loadConversations(); loadNotifications(); try{ loadFriends(); }catch(e){} });
    }
    // apply preferred theme
    try{ applyTheme(getPreferredTheme()); }catch(e){}
    // Prevent any uploaded image from accidentally becoming the page background
    // ensure sidebars are visible on wider screens
    try{ if (window.innerWidth<=900){ const left = q('sidebar-left'), right = q('sidebar-right'); if (left) left.style.display='none'; if (right) right.style.display='none'; } }catch(e){}
  }

  document.addEventListener('DOMContentLoaded', init);
})();
