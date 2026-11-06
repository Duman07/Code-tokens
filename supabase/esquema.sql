-- Contador de tokens: esquema de Supabase.
-- Pégalo entero en el SQL Editor de tu proyecto (Dashboard → SQL Editor → New query → Run).
-- Se puede volver a ejecutar sin problemas.
--
-- Cómo encaja:
--   * Claude Code (el mod) envía cada medición con un TOKEN DE DISPOSITIVO a la función
--     reportar_uso(). No guarda tu contraseña ni tu sesión de Supabase.
--   * Las apps (Windows y Android) inician sesión con tu correo y contraseña de Supabase
--     y leen solo tus filas (RLS).
--   * El token de dispositivo lo genera la app de Windows (Ajustes → Generar token),
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
create or replace function public.crear_token_dispositivo(p_nombre text)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  t text;
begin
  if auth.uid() is null then
    raise exception 'Inicia sesión para crear un token';
  end if;
  t := 'ctk_' || encode(gen_random_bytes(24), 'hex');
  insert into tokens_dispositivo (user_id, nombre, hash)
  values (auth.uid(), coalesce(nullif(trim(p_nombre), ''), 'Equipo'), encode(digest(t, 'sha256'), 'hex'));
  return t; -- se muestra una sola vez
end;
$$;
revoke all on function public.crear_token_dispositivo(text) from public, anon;
grant execute on function public.crear_token_dispositivo(text) to authenticated;

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
  v_sesion text := left(p_datos ->> 'sesion', 100);
  v_prev jsonb;
  v_prev_costo numeric;
  d_entrada bigint;
  d_salida bigint;
  d_leida bigint;
  d_escrita bigint;
  d_costo numeric;
begin
  select id, user_id, nombre into v_id, v_user, v_disp
  from tokens_dispositivo
  where hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');

  if v_user is null then
    raise exception 'Token de dispositivo no válido' using errcode = '28000';
  end if;
  if coalesce(p_datos ->> 'sesion', '') = '' then
    raise exception 'Falta la sesión';
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
    insert into uso_hora as h (user_id, sesion, hora, dispositivo, proyecto, entrada, salida, cache_leida, cache_escrita, costo_usd)
    values (v_user, v_sesion, date_trunc('hour', now()), v_disp, left(p_datos ->> 'proyecto', 200),
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
    contexto, ventana, porcentaje, costo_usd, modelo, totales, limites, historial
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
    coalesce(p_datos -> 'historial', '[]'::jsonb)
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
    historial   = excluded.historial;

  -- Limpieza: el detalle de sesiones a los 30 días; el consumo por hora se guarda un año.
  delete from uso_sesion where user_id = v_user and actualizado < now() - interval '30 days';
  delete from uso_hora where user_id = v_user and hora < now() - interval '400 days';
end;
$$;
revoke all on function public.reportar_uso(text, jsonb) from public;
grant execute on function public.reportar_uso(text, jsonb) to anon, authenticated;

-- (Opcional) Cambios en tiempo real para futuras versiones de las apps:
-- alter publication supabase_realtime add table public.uso_sesion;
