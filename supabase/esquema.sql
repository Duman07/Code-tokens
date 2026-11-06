-- Contador de tokens: esquema de Supabase.
-- Pégalo entero en el SQL Editor de tu proyecto (Dashboard → SQL Editor → New query → Run).
-- Se puede volver a ejecutar sin problemas.
--
-- Cómo encaja:
--   * Claude Code (el mod) envía cada medición con un TOKEN DE DISPOSITIVO a la función
--     reportar_uso(). No guarda tu contraseña ni tu sesión de Supabase.
--   * Las apps (Windows y Android) inician sesión con tu correo y contraseña de Supabase
--     y leen solo tus filas (RLS).
--   * Cualquiera puede registrarse desde la app (Authentication → Providers → Email activado).
--     La app muestra un código corto y en Claude Code: /tokens vincular CODIGO.
--   * También se puede generar un token a mano en la app de Windows (Ajustes → Generar token),
--     o con: select crear_token_dispositivo('Mi PC');  ejecutado como usuario autenticado.

create extension if not exists pgcrypto with schema extensions;

-- ---------- Tokens de dispositivo (solo se guarda el hash) ----------
create table if not exists public.tokens_dispositivo (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users on delete cascade,
  nombre text not null,
  hash text not null unique,
  creado timestamptz not null default now(),
  usado timestamptz
);
alter table public.tokens_dispositivo enable row level security;

drop policy if exists "ver mis dispositivos" on public.tokens_dispositivo;
create policy "ver mis dispositivos" on public.tokens_dispositivo
  for select to authenticated using (auth.uid() = user_id);
drop policy if exists "borrar mis dispositivos" on public.tokens_dispositivo;
create policy "borrar mis dispositivos" on public.tokens_dispositivo
  for delete to authenticated using (auth.uid() = user_id);

-- ---------- Cuentas de IA conectadas (Claude, Codex, Cursor, Antigravity…) ----------
-- Regla de negocio: cada usuario puede tener UNA sola cuenta de Claude; de los demás proveedores
-- puede tener las que quiera (hasta 10 en total). Varios equipos pueden compartir la misma cuenta.
-- Nota: Claude Code no le dice al mod con qué cuenta está iniciado, así que la regla se aplica aquí,
-- sobre la cuenta que el usuario declara en la app, no contra Anthropic.
create table if not exists public.cuentas_ia (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users on delete cascade,
  proveedor text not null check (proveedor in ('claude', 'codex', 'cursor', 'antigravity', 'otro')),
  etiqueta text not null,
  creado timestamptz not null default now()
);
create unique index if not exists una_cuenta_claude on public.cuentas_ia (user_id) where proveedor = 'claude';
alter table public.cuentas_ia enable row level security;
drop policy if exists "ver mis cuentas" on public.cuentas_ia;
create policy "ver mis cuentas" on public.cuentas_ia for select to authenticated using (auth.uid() = user_id);
drop policy if exists "borrar mis cuentas" on public.cuentas_ia;
create policy "borrar mis cuentas" on public.cuentas_ia for delete to authenticated using (auth.uid() = user_id);

alter table public.tokens_dispositivo add column if not exists cuenta_id uuid references public.cuentas_ia on delete cascade;

-- Lo que ya existía era todo de Claude: cada usuario con llaves recibe su cuenta de Claude.
insert into public.cuentas_ia (user_id, proveedor, etiqueta)
select distinct user_id, 'claude', 'Claude' from public.tokens_dispositivo t
where cuenta_id is null
on conflict do nothing;
update public.tokens_dispositivo t set cuenta_id = c.id
from public.cuentas_ia c
where t.cuenta_id is null and c.user_id = t.user_id and c.proveedor = 'claude';

create or replace function public.crear_cuenta_ia(p_proveedor text, p_etiqueta text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'Inicia sesión'; end if;
  if (select count(*) from cuentas_ia where user_id = auth.uid()) >= 10 then
    raise exception 'Máximo 10 cuentas conectadas';
  end if;
  begin
    insert into cuentas_ia (user_id, proveedor, etiqueta)
    values (auth.uid(), lower(trim(p_proveedor)), coalesce(nullif(left(trim(p_etiqueta), 60), ''), initcap(p_proveedor)))
    returning id into v_id;
  exception when unique_violation then
    raise exception 'Solo puedes conectar una cuenta de Claude. Puedes añadir más equipos a esa misma cuenta.';
  when check_violation then
    raise exception 'Proveedor no admitido';
  end;
  return v_id;
end;
$$;
revoke all on function public.crear_cuenta_ia(text, text) from public, anon;
grant execute on function public.crear_cuenta_ia(text, text) to authenticated;

-- Cuenta a la que va un código o una llave: la indicada (si es mía) o, si no se indica, la de Claude
-- (que se crea si falta). Así el flujo de /tokens vincular de siempre sigue funcionando.
create or replace function public.cuenta_para(p_usuario uuid, p_cuenta uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  if p_cuenta is not null then
    select id into v_id from cuentas_ia where id = p_cuenta and user_id = p_usuario;
    if v_id is null then raise exception 'Cuenta no encontrada'; end if;
    return v_id;
  end if;
  select id into v_id from cuentas_ia where user_id = p_usuario and proveedor = 'claude';
  if v_id is null then
    insert into cuentas_ia (user_id, proveedor, etiqueta) values (p_usuario, 'claude', 'Claude') returning id into v_id;
  end if;
  return v_id;
end;
$$;
revoke all on function public.cuenta_para(uuid, uuid) from public, anon, authenticated;

-- ---------- Uso por sesión de Claude Code ----------
create table if not exists public.uso_sesion (
  user_id uuid not null references auth.users on delete cascade,
  sesion text not null,
  dispositivo text not null,
  proyecto text,
  actualizado timestamptz not null default now(),
  contexto integer,
  ventana integer,
  porcentaje integer,
  costo_usd numeric(12, 4),
  modelo text,
  totales jsonb not null default '{}'::jsonb,   -- {entrada, salida, cacheLeida, cacheEscrita, turnos, turnosSubagentes}
  limites jsonb not null default '[]'::jsonb,   -- [{tipo, usado, reinicia}]
  historial jsonb not null default '[]'::jsonb, -- [{entrada, salida, cache}]
  primary key (user_id, sesion)
);
alter table public.uso_sesion add column if not exists proveedor text not null default 'claude';
alter table public.uso_sesion add column if not exists cuenta_id uuid;
create index if not exists uso_sesion_reciente on public.uso_sesion (user_id, actualizado desc);
alter table public.uso_sesion enable row level security;

drop policy if exists "ver mi uso" on public.uso_sesion;
create policy "ver mi uso" on public.uso_sesion
  for select to authenticated using (auth.uid() = user_id);
drop policy if exists "borrar mi uso" on public.uso_sesion;
create policy "borrar mi uso" on public.uso_sesion
  for delete to authenticated using (auth.uid() = user_id);

-- ---------- Consumo por hora y sesión (para día / semana / mes) ----------
-- uso_sesion guarda el acumulado de cada sesión; aquí se guarda lo que se gastó en cada
-- hora, para repartir bien una sesión que dura varios días. Se llena en reportar_uso().
create table if not exists public.uso_hora (
  user_id uuid not null references auth.users on delete cascade,
  sesion text not null,
  hora timestamptz not null,           -- inicio de la hora (UTC)
  dispositivo text not null,
  proyecto text,
  entrada bigint not null default 0,
  salida bigint not null default 0,
  cache_leida bigint not null default 0,
  cache_escrita bigint not null default 0,
  costo_usd numeric(12, 4) not null default 0,
  primary key (user_id, sesion, hora)
);
alter table public.uso_hora add column if not exists proveedor text not null default 'claude';
create index if not exists uso_hora_reciente on public.uso_hora (user_id, hora desc);
alter table public.uso_hora enable row level security;

drop policy if exists "ver mi consumo" on public.uso_hora;
create policy "ver mi consumo" on public.uso_hora
  for select to authenticated using (auth.uid() = user_id);
drop policy if exists "borrar mi consumo" on public.uso_hora;
create policy "borrar mi consumo" on public.uso_hora
  for delete to authenticated using (auth.uid() = user_id);

-- Lo que ya había antes de esta tabla: cada sesión entera en la hora de su última medición.
-- Solo para sesiones que todavía no tienen filas aquí, así que se puede volver a ejecutar.
insert into public.uso_hora (user_id, sesion, hora, dispositivo, proyecto, entrada, salida, cache_leida, cache_escrita, costo_usd)
select u.user_id, u.sesion, date_trunc('hour', u.actualizado), u.dispositivo, u.proyecto,
       coalesce((u.totales ->> 'entrada')::bigint, 0), coalesce((u.totales ->> 'salida')::bigint, 0),
       coalesce((u.totales ->> 'cacheLeida')::bigint, 0), coalesce((u.totales ->> 'cacheEscrita')::bigint, 0),
       coalesce(u.costo_usd, 0)
from public.uso_sesion u
where not exists (select 1 from public.uso_hora h where h.user_id = u.user_id and h.sesion = u.sesion);

-- Consumo agrupado por día local y sesión, desde p_desde. La app lo llama con su zona horaria.
create or replace function public.consumo(p_desde timestamptz, p_zona text default 'UTC')
returns table (
  dia date, sesion text, proyecto text, dispositivo text,
  entrada bigint, salida bigint, cache_leida bigint, cache_escrita bigint, costo_usd numeric
)
language sql
stable
security invoker
set search_path = public
as $$
  select (h.hora at time zone p_zona)::date as dia, h.sesion, max(h.proyecto), max(h.dispositivo),
         sum(h.entrada)::bigint, sum(h.salida)::bigint, sum(h.cache_leida)::bigint, sum(h.cache_escrita)::bigint,
         sum(h.costo_usd)
  from uso_hora h
  where h.user_id = auth.uid() and h.hora >= p_desde
  group by 1, 2
  order by 1, 2;
$$;
revoke all on function public.consumo(timestamptz, text) from public, anon;
grant execute on function public.consumo(timestamptz, text) to authenticated;

-- ---------- Crear un token de dispositivo (lo llama la app, con sesión iniciada) ----------
drop function if exists public.crear_token_dispositivo(text);
create or replace function public.crear_token_dispositivo(p_nombre text, p_cuenta uuid default null)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  t text;
  v_cuenta uuid;
begin
  if auth.uid() is null then
    raise exception 'Inicia sesión para crear un token';
  end if;
  if (select count(*) from tokens_dispositivo where user_id = auth.uid()) >= 20 then
    raise exception 'Máximo 20 llaves; revoca alguna antes de crear otra';
  end if;
  v_cuenta := cuenta_para(auth.uid(), p_cuenta);
  t := 'ctk_' || encode(gen_random_bytes(24), 'hex');
  insert into tokens_dispositivo (user_id, nombre, hash, cuenta_id)
  values (auth.uid(), coalesce(nullif(trim(p_nombre), ''), 'Equipo'), encode(digest(t, 'sha256'), 'hex'), v_cuenta);
  return t; -- se muestra una sola vez
end;
$$;
revoke all on function public.crear_token_dispositivo(text, uuid) from public, anon;
grant execute on function public.crear_token_dispositivo(text, uuid) to authenticated;

-- ---------- Eliminar mi cuenta (lo exige Google Play) ----------
-- Borra al usuario de auth.users; todo lo demás cae en cascada (tokens, sesiones, consumo, códigos).
create or replace function public.eliminar_mi_cuenta()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if auth.uid() is null then
    raise exception 'Inicia sesión';
  end if;
  delete from auth.users where id = auth.uid();
end;
$$;
revoke all on function public.eliminar_mi_cuenta() from public, anon;
grant execute on function public.eliminar_mi_cuenta() to authenticated;

-- ---------- Vincular Claude Code con un código corto ----------
-- La app (con sesión iniciada) pide un código de 8 caracteres que vale 10 minutos. En Claude Code
-- se escribe `/tokens vincular CODIGO` y el mod lo canjea por un token de dispositivo.
-- Así nadie copia tokens largos y el token nunca pasa por el chat ni por el portapapeles.
create table if not exists public.codigos_vinculo (
  hash text primary key,
  user_id uuid not null references auth.users on delete cascade,
  creado timestamptz not null default now(),
  cuenta_id uuid
);
alter table public.codigos_vinculo add column if not exists cuenta_id uuid;
alter table public.codigos_vinculo enable row level security; -- sin políticas: solo las funciones lo tocan

drop function if exists public.crear_codigo_vinculo();
create or replace function public.crear_codigo_vinculo(p_cuenta uuid default null)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  alfabeto constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; -- sin 0/O/1/I/L
  c text := '';
  i int;
  v_cuenta uuid;
begin
  if auth.uid() is null then
    raise exception 'Inicia sesión para vincular Claude Code';
  end if;
  v_cuenta := cuenta_para(auth.uid(), p_cuenta);
  delete from codigos_vinculo where creado < now() - interval '10 minutes';
  -- Máximo 5 códigos vivos por usuario.
  if (select count(*) from codigos_vinculo where user_id = auth.uid()) >= 5 then
    raise exception 'Demasiados códigos pendientes; espera unos minutos';
  end if;
  for i in 1..8 loop
    c := c || substr(alfabeto, 1 + (get_byte(gen_random_bytes(1), 0) % length(alfabeto)), 1);
  end loop;
  insert into codigos_vinculo (hash, user_id, cuenta_id) values (encode(digest(c, 'sha256'), 'hex'), auth.uid(), v_cuenta);
  return c;
end;
$$;
revoke all on function public.crear_codigo_vinculo(uuid) from public, anon;
grant execute on function public.crear_codigo_vinculo(uuid) to authenticated;

-- Intentos fallidos por IP no se pueden medir aquí; el espacio de códigos (31^8) y los 10 minutos
-- de vida bastan, y cada código se usa una sola vez.
create or replace function public.canjear_codigo_vinculo(p_codigo text, p_nombre text default 'Claude Code')
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_hash text := encode(digest(upper(regexp_replace(coalesce(p_codigo, ''), '[^A-Za-z0-9]', '', 'g')), 'sha256'), 'hex');
  v_user uuid;
  v_cuenta uuid;
  t text;
begin
  delete from codigos_vinculo where hash = v_hash and creado >= now() - interval '10 minutes'
  returning user_id, cuenta_id into v_user, v_cuenta;
  if v_user is null then
    raise exception 'Código no válido o vencido' using errcode = '28000';
  end if;
  if (select count(*) from tokens_dispositivo where user_id = v_user) >= 20 then
    raise exception 'Máximo 20 llaves; revoca alguna en la app antes de vincular otra';
  end if;
  t := 'ctk_' || encode(gen_random_bytes(24), 'hex');
  insert into tokens_dispositivo (user_id, nombre, hash, cuenta_id)
  values (v_user, coalesce(nullif(left(trim(p_nombre), 60), ''), 'Claude Code'), encode(digest(t, 'sha256'), 'hex'), v_cuenta);
  return t;
end;
$$;
revoke all on function public.canjear_codigo_vinculo(text, text) from public;
grant execute on function public.canjear_codigo_vinculo(text, text) to anon, authenticated;

-- ---------- Recibir una medición desde Claude Code ----------
create or replace function public.reportar_uso(p_token text, p_datos jsonb)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_user uuid;
  v_disp text;
  v_id uuid;
  v_cuenta uuid;
  v_prov text := 'claude';
  v_sesion text := left(p_datos ->> 'sesion', 100);
  v_prev jsonb;
  v_prev_costo numeric;
  d_entrada bigint;
  d_salida bigint;
  d_leida bigint;
  d_escrita bigint;
  d_costo numeric;
begin
  select t.id, t.user_id, t.nombre, t.cuenta_id, coalesce(c.proveedor, 'claude') into v_id, v_user, v_disp, v_cuenta, v_prov
  from tokens_dispositivo t left join cuentas_ia c on c.id = t.cuenta_id
  where t.hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');

  if v_user is null then
    raise exception 'Token de dispositivo no válido' using errcode = '28000';
  end if;
  if coalesce(p_datos ->> 'sesion', '') = '' then
    raise exception 'Falta la sesión';
  end if;
  -- Una medición real pesa unos pocos KB; esto corta abusos con un token válido.
  if length(p_datos::text) > 30000 or jsonb_array_length(coalesce(p_datos -> 'historial', '[]'::jsonb)) > 40
     or jsonb_array_length(coalesce(p_datos -> 'limites', '[]'::jsonb)) > 10 then
    raise exception 'Medición demasiado grande';
  end if;
  if (select count(*) from uso_sesion where user_id = v_user) >= 500
     and not exists (select 1 from uso_sesion where user_id = v_user and sesion = v_sesion) then
    raise exception 'Demasiadas sesiones guardadas; borra algunas en la app';
  end if;

  update tokens_dispositivo set usado = now() where id = v_id;

  -- Lo que se gastó desde la medición anterior (el mod manda acumulados). Si bajó,
  -- el contador se reinició y todo lo que llega es nuevo.
  select totales, coalesce(costo_usd, 0) into v_prev, v_prev_costo
  from uso_sesion where user_id = v_user and sesion = v_sesion;
  v_prev := coalesce(v_prev, '{}'::jsonb);
  v_prev_costo := coalesce(v_prev_costo, 0);
  d_entrada := coalesce((p_datos #>> '{totales,entrada}')::bigint, 0);
  d_salida  := coalesce((p_datos #>> '{totales,salida}')::bigint, 0);
  d_leida   := coalesce((p_datos #>> '{totales,cacheLeida}')::bigint, 0);
  d_escrita := coalesce((p_datos #>> '{totales,cacheEscrita}')::bigint, 0);
  d_costo   := coalesce((p_datos ->> 'costoUsd')::numeric, 0);
  if d_entrada >= coalesce((v_prev ->> 'entrada')::bigint, 0) then d_entrada := d_entrada - coalesce((v_prev ->> 'entrada')::bigint, 0); end if;
  if d_salida  >= coalesce((v_prev ->> 'salida')::bigint, 0) then d_salida := d_salida - coalesce((v_prev ->> 'salida')::bigint, 0); end if;
  if d_leida   >= coalesce((v_prev ->> 'cacheLeida')::bigint, 0) then d_leida := d_leida - coalesce((v_prev ->> 'cacheLeida')::bigint, 0); end if;
  if d_escrita >= coalesce((v_prev ->> 'cacheEscrita')::bigint, 0) then d_escrita := d_escrita - coalesce((v_prev ->> 'cacheEscrita')::bigint, 0); end if;
  if d_costo   >= v_prev_costo then d_costo := d_costo - v_prev_costo; end if;

  if d_entrada + d_salida + d_leida + d_escrita > 0 or d_costo > 0 then
    insert into uso_hora as h (user_id, sesion, hora, dispositivo, proyecto, proveedor, entrada, salida, cache_leida, cache_escrita, costo_usd)
    values (v_user, v_sesion, date_trunc('hour', now()), v_disp, left(p_datos ->> 'proyecto', 200), v_prov,
            d_entrada, d_salida, d_leida, d_escrita, d_costo)
    on conflict (user_id, sesion, hora) do update set
      dispositivo   = excluded.dispositivo,
      proyecto      = excluded.proyecto,
      entrada       = h.entrada + excluded.entrada,
      salida        = h.salida + excluded.salida,
      cache_leida   = h.cache_leida + excluded.cache_leida,
      cache_escrita = h.cache_escrita + excluded.cache_escrita,
      costo_usd     = h.costo_usd + excluded.costo_usd;
  end if;

  insert into uso_sesion as u (
    user_id, sesion, dispositivo, proyecto, actualizado,
    contexto, ventana, porcentaje, costo_usd, modelo, totales, limites, historial, proveedor, cuenta_id
  ) values (
    v_user,
    v_sesion,
    v_disp,
    left(p_datos ->> 'proyecto', 200),
    now(),
    (p_datos ->> 'contexto')::integer,
    (p_datos ->> 'ventana')::integer,
    (p_datos ->> 'porcentaje')::integer,
    (p_datos ->> 'costoUsd')::numeric,
    left(p_datos ->> 'modelo', 100),
    coalesce(p_datos -> 'totales', '{}'::jsonb),
    coalesce(p_datos -> 'limites', '[]'::jsonb),
    coalesce(p_datos -> 'historial', '[]'::jsonb),
    v_prov,
    v_cuenta
  )
  on conflict (user_id, sesion) do update set
    dispositivo = excluded.dispositivo,
    proyecto    = excluded.proyecto,
    actualizado = excluded.actualizado,
    contexto    = excluded.contexto,
    ventana     = excluded.ventana,
    porcentaje  = excluded.porcentaje,
    costo_usd   = excluded.costo_usd,
    modelo      = excluded.modelo,
    totales     = excluded.totales,
    limites     = excluded.limites,
    historial   = excluded.historial,
    proveedor   = excluded.proveedor,
    cuenta_id   = excluded.cuenta_id;

  -- Limpieza: el detalle de sesiones a los 30 días; el consumo por hora se guarda un año.
  delete from uso_sesion where user_id = v_user and actualizado < now() - interval '30 days';
  delete from uso_hora where user_id = v_user and hora < now() - interval '400 days';
end;
$$;
revoke all on function public.reportar_uso(text, jsonb) from public;
grant execute on function public.reportar_uso(text, jsonb) to anon, authenticated;

-- (Opcional) Cambios en tiempo real para futuras versiones de las apps:
-- alter publication supabase_realtime add table public.uso_sesion;
