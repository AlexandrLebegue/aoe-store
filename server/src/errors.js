export class HttpError extends Error {
  constructor(status, code, message, details = undefined) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function sendJson(res, status, payload) {
  const body = Buffer.from(`${JSON.stringify(payload)}\n`, 'utf8');
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Cache-Control': 'no-store',
  });
  res.end(res.req?.method === 'HEAD' ? undefined : body);
}

export function sendError(res, error) {
  if (error instanceof HttpError) {
    const payload = { error: { code: error.code, message: error.message } };
    if (error.details !== undefined) payload.error.details = error.details;
    sendJson(res, error.status, payload);
    return;
  }
  sendJson(res, 500, {
    error: {
      code: 'internal_error',
      message: 'Erreur interne du serveur',
    },
  });
}

export function methodNotAllowed(res, allowed) {
  res.setHeader('Allow', allowed.join(', '));
  sendError(
    res,
    new HttpError(405, 'method_not_allowed', `Méthode non autorisée. Méthodes acceptées : ${allowed.join(', ')}`),
  );
}
