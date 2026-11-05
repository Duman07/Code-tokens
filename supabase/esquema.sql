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

  insert into uso_sesion as u (
    user_id, sesion, dispositivo, proyecto, actualizado,
    contexto, ventana, porcentaje, costo_usd, modelo, totales, limites, historial
  ) values (
    v_user,
    left(p_datos ->> 'sesion', 100),
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

  -- Limpieza: sesiones de más de 30 días.
  delete from uso_sesion where user_id = v_user and actualizado < now() - interval '30 days';
end;
$$;
revoke all on function public.reportar_uso(text, jsonb) from public;
grant execute on function public.reportar_uso(text, jsonb) to anon, authenticated;

-- (Opcional) Cambios en tiempo real para futuras versiones de las apps:
-- alter publication supabase_realtime add table public.uso_sesion;
